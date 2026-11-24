import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  InvalidOvsfEnvelopeError,
  KdfType,
  MacVerificationError,
  OVSF_OVERHEAD_BYTES,
  SAFE_MAX_FILE_BYTES,
  base64ToBytes,
  bytesToBase64,
  bytesToHex,
  bytesToUtf8,
  decryptSafeFileBinary,
  decryptSafeMetadata,
  encryptSafeFileBinary,
  encryptSafeMetadata,
  formatRecoveryKey,
  generateFileKey,
  generateRecoveryKey,
  generateSafeKey,
  hexToBytes,
  parseRecoveryKey,
  protectSafeKeyWithPin,
  protectSafeKeyWithRecoveryKey,
  sha256HexBytes,
  splitSymmetricKey,
  unprotectSafeKeyWithPin,
  unprotectSafeKeyWithRecoveryKey,
  utf8ToBytes,
  validateSafePin,
} from "../src/index.js";

const vectorsPath = resolve(import.meta.dirname, "../../../spec/test-vectors.json");

interface SafeTestVectors {
  personalSafe: {
    safeKeyHex: string;
    pinWrap: {
      pin: string;
      saltB64: string;
      ivHex: string;
      kdfType: KdfType;
      kdfIterations: number;
      kdfMemoryMiB: number;
      kdfParallelism: number;
      expectedEncryptedSafeKey: string;
    };
    recoveryKey: {
      rawHex: string;
      expectedFormatted: string;
      saltB64: string;
      ivHex: string;
      iterations: number;
      expectedEncryptedSafeKey: string;
    };
    ovsfBinary: {
      itemId: string;
      fileKeyHex: string;
      ivHex: string;
      plaintextUtf8: string;
      expectedBlobB64: string;
      expectedThumbB64: string;
    };
  };
}

function loadSafeVectors() {
  return (JSON.parse(readFileSync(vectorsPath, "utf8")) as SafeTestVectors).personalSafe;
}

describe("@openvault/crypto-core — Vault+ (Personal Safe)", () => {
  it("matches cross-platform PIN wrap/unwrap vector", async () => {
    const v = loadSafeVectors();
    const safeKey = splitSymmetricKey(hexToBytes(v.safeKeyHex));

    const protectedPin = await protectSafeKeyWithPin(
      safeKey,
      v.pinWrap.pin,
      {
        kdf: v.pinWrap.kdfType,
        kdfIterations: v.pinWrap.kdfIterations,
        kdfMemory: v.pinWrap.kdfMemoryMiB,
        kdfParallelism: v.pinWrap.kdfParallelism,
      },
      base64ToBytes(v.pinWrap.saltB64),
      hexToBytes(v.pinWrap.ivHex),
    );

    expect(protectedPin.encryptedSafeKey).toBe(v.pinWrap.expectedEncryptedSafeKey);

    const unwrapped = await unprotectSafeKeyWithPin(
      v.pinWrap.expectedEncryptedSafeKey,
      v.pinWrap.pin,
      v.pinWrap.saltB64,
      {
        kdf: v.pinWrap.kdfType,
        kdfIterations: v.pinWrap.kdfIterations,
        kdfMemory: v.pinWrap.kdfMemoryMiB,
        kdfParallelism: v.pinWrap.kdfParallelism,
      },
    );
    expect(bytesToHex(unwrapped.raw)).toBe(v.safeKeyHex);

    await expect(
      unprotectSafeKeyWithPin(
        v.pinWrap.expectedEncryptedSafeKey,
        "000000",
        v.pinWrap.saltB64,
        {
          kdf: v.pinWrap.kdfType,
          kdfIterations: v.pinWrap.kdfIterations,
          kdfMemory: v.pinWrap.kdfMemoryMiB,
          kdfParallelism: v.pinWrap.kdfParallelism,
        },
      ),
    ).rejects.toThrow(MacVerificationError);
  });

  it("matches Crockford Base32 Recovery Key format, typo normalization, and PBKDF2 wrapping vector", async () => {
    const v = loadSafeVectors();
    const safeKey = splitSymmetricKey(hexToBytes(v.safeKeyHex));
    const rawRecovery = hexToBytes(v.recoveryKey.rawHex);

    const formatted = formatRecoveryKey(rawRecovery);
    expect(formatted).toBe(v.recoveryKey.expectedFormatted);

    // Lowercase + spaces + typo normalization (O->0, i/l->1)
    const messyInput = formatted
      .toLowerCase()
      .replace("0410", "o4io")
      .replace("6105", "6Lo5")
      .replace(/-/g, " ");
    expect(bytesToHex(parseRecoveryKey(messyInput))).toBe(v.recoveryKey.rawHex);

    const wrapped = await protectSafeKeyWithRecoveryKey(
      safeKey,
      formatted,
      v.recoveryKey.iterations,
      base64ToBytes(v.recoveryKey.saltB64),
      hexToBytes(v.recoveryKey.ivHex),
    );
    expect(wrapped.encryptedSafeKey).toBe(v.recoveryKey.expectedEncryptedSafeKey);

    const unwrapped = await unprotectSafeKeyWithRecoveryKey(
      v.recoveryKey.expectedEncryptedSafeKey,
      messyInput,
      v.recoveryKey.saltB64,
      v.recoveryKey.iterations,
    );
    expect(bytesToHex(unwrapped.raw)).toBe(v.safeKeyHex);

    const wrongRecovery = generateRecoveryKey().formatted;
    await expect(
      unprotectSafeKeyWithRecoveryKey(
        v.recoveryKey.expectedEncryptedSafeKey,
        wrongRecovery,
        v.recoveryKey.saltB64,
        v.recoveryKey.iterations,
      ),
    ).rejects.toThrow(MacVerificationError);
  });

  it("matches OVSF v1 binary envelope vectors and enforces AAD & integrity checks", async () => {
    const v = loadSafeVectors().ovsfBinary;
    const fileKey = hexToBytes(v.fileKeyHex);
    const iv = hexToBytes(v.ivHex);
    const plain = utf8ToBytes(v.plaintextUtf8);

    const blobEnvelope = await encryptSafeFileBinary(plain, fileKey, v.itemId, "blob", iv);
    expect(bytesToBase64(blobEnvelope)).toBe(v.expectedBlobB64);
    expect(blobEnvelope.byteLength).toBe(plain.byteLength + OVSF_OVERHEAD_BYTES);

    const thumbEnvelope = await encryptSafeFileBinary(plain, fileKey, v.itemId, "thumb", iv);
    expect(bytesToBase64(thumbEnvelope)).toBe(v.expectedThumbB64);

    const decBlob = await decryptSafeFileBinary(blobEnvelope, fileKey, v.itemId, "blob");
    expect(bytesToUtf8(decBlob)).toBe(v.plaintextUtf8);

    const decThumb = await decryptSafeFileBinary(thumbEnvelope, fileKey, v.itemId, "thumb");
    expect(bytesToUtf8(decThumb)).toBe(v.plaintextUtf8);

    // Wrong AAD itemId or swapped kind must fail GCM verification
    await expect(
      decryptSafeFileBinary(blobEnvelope, fileKey, "00000000-0000-0000-0000-000000000000", "blob"),
    ).rejects.toThrow(MacVerificationError);
    await expect(
      decryptSafeFileBinary(blobEnvelope, fileKey, v.itemId, "thumb"),
    ).rejects.toThrow(MacVerificationError);

    // 1-bit tamper inside auth tag fails
    const tampered = new Uint8Array(blobEnvelope);
    const lastIdx = tampered.byteLength - 1;
    tampered[lastIdx] = (tampered[lastIdx] ?? 0) ^ 0x01;
    await expect(decryptSafeFileBinary(tampered, fileKey, v.itemId, "blob")).rejects.toThrow(
      MacVerificationError,
    );

    // Corrupt magic fails immediately
    const badMagic = new Uint8Array(blobEnvelope);
    badMagic[0] = 0x00;
    await expect(decryptSafeFileBinary(badMagic, fileKey, v.itemId, "blob")).rejects.toThrow(
      InvalidOvsfEnvelopeError,
    );
  });

  it("validates 6-digit PIN rules, metadata round-trip, and 5 MiB binary round-trip", async () => {
    expect(() => validateSafePin("123456")).not.toThrow();
    expect(() => validateSafePin("12345")).toThrow();
    expect(() => validateSafePin("12345a")).toThrow();
    expect(() => validateSafePin("1234567")).toThrow();

    const safeKey = generateSafeKey();
    const fileKey = generateFileKey();
    const itemId = "e2b7a980-4c11-4090-9f5c-3318b98a1122";

    // Full 5 MiB payload encrypt/decrypt
    const fiveMib = new Uint8Array(SAFE_MAX_FILE_BYTES);
    for (let i = 0; i < fiveMib.length; i += 4096) {
      fiveMib[i] = i & 0xff;
    }
    const sha = await sha256HexBytes(fiveMib);
    const env = await encryptSafeFileBinary(fiveMib, fileKey, itemId, "blob");
    expect(env.byteLength).toBe(SAFE_MAX_FILE_BYTES + OVSF_OVERHEAD_BYTES);
    const decrypted = await decryptSafeFileBinary(env, fileKey, itemId, "blob");
    expect(decrypted.byteLength).toBe(SAFE_MAX_FILE_BYTES);
    expect(await sha256HexBytes(decrypted)).toBe(sha);

    // Oversize > 5 MiB rejected
    await expect(
      encryptSafeFileBinary(new Uint8Array(SAFE_MAX_FILE_BYTES + 1), fileKey, itemId, "blob"),
    ).rejects.toThrow();

    // Metadata Type 2 round-trip
    const encMeta = await encryptSafeMetadata(
      {
        v: 1,
        title: "Căn cước công dân",
        fileName: "cccd.pdf",
        mimeType: "application/pdf",
        category: "id_card",
        notes: "Bản scan 2 mặt",
        fileKeyB64: bytesToBase64(fileKey),
        thumbKeyB64: null,
        originalSize: SAFE_MAX_FILE_BYTES,
        originalSha256: sha,
        expiresAt: "2035-08-19",
      },
      safeKey,
    );
    const decMeta = await decryptSafeMetadata(encMeta, safeKey);
    expect(decMeta.title).toBe("Căn cước công dân");
    expect(decMeta.category).toBe("id_card");
    expect(decMeta.originalSha256).toBe(sha);
    expect(decMeta.expiresAt).toBe("2035-08-19");
  });
});
