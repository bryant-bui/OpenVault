import { argon2id } from "hash-wasm";
import {
  bytesToBase64,
  concatBytes,
  toArrayBufferView,
  utf8ToBytes,
} from "./bytes.js";
import { KdfType, type KdfConfig, type SymmetricKey } from "./types.js";

export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

export function validateKdfConfig(config: KdfConfig): void {
  if (config.kdf === KdfType.PBKDF2_SHA256) {
    if (!Number.isInteger(config.kdfIterations) || config.kdfIterations < 100_000 || config.kdfIterations > 2_000_000) {
      throw new Error(`Invalid PBKDF2 iterations: ${config.kdfIterations} (expected 100000..2000000)`);
    }
    return;
  }

  if (config.kdf === KdfType.Argon2id) {
    const iterations = config.kdfIterations;
    const memory = config.kdfMemory ?? 64;
    const parallelism = config.kdfParallelism ?? 4;
    if (!Number.isInteger(iterations) || iterations < 1 || iterations > 10) {
      throw new Error(`Invalid Argon2id iterations: ${iterations} (expected 1..10)`);
    }
    if (!Number.isInteger(memory) || memory < 15 || memory > 1024) {
      throw new Error(`Invalid Argon2id memory: ${memory} MiB (expected 15..1024)`);
    }
    if (!Number.isInteger(parallelism) || parallelism < 1 || parallelism > 16) {
      throw new Error(`Invalid Argon2id parallelism: ${parallelism} (expected 1..16)`);
    }
    return;
  }

  throw new Error(`Unsupported KDF type: ${String(config.kdf)}`);
}

async function pbkdf2Sha256(
  passwordBytes: Uint8Array,
  saltBytes: Uint8Array,
  iterations: number,
  lengthBytes: number,
): Promise<Uint8Array<ArrayBuffer>> {
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(passwordBytes),
    { name: "PBKDF2" },
    false,
    ["deriveBits"],
  );

  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: toArrayBufferView(saltBytes),
      iterations,
    },
    keyMaterial,
    lengthBytes * 8,
  );

  return new Uint8Array(derivedBits);
}

/**
 * Derives the 32-byte Bitwarden Master Key client-side.
 * - KdfType.PBKDF2_SHA256 (0): salt = UTF8(normalizedEmail)
 * - KdfType.Argon2id (1): salt = SHA-256(UTF8(normalizedEmail)), memory = kdfMemory * 1024 KiB
 */
export async function deriveMasterKey(
  masterPassword: string,
  email: string,
  config: KdfConfig,
): Promise<Uint8Array<ArrayBuffer>> {
  validateKdfConfig(config);
  const passwordBytes = utf8ToBytes(masterPassword);
  const emailBytes = utf8ToBytes(normalizeEmail(email));

  if (config.kdf === KdfType.PBKDF2_SHA256) {
    return pbkdf2Sha256(passwordBytes, emailBytes, config.kdfIterations, 32);
  }

  const saltDigest = new Uint8Array(await crypto.subtle.digest("SHA-256", emailBytes));
  const memoryKiB = (config.kdfMemory ?? 64) * 1024;
  const parallelism = config.kdfParallelism ?? 4;

  const rawHash = await argon2id({
    password: passwordBytes,
    salt: saltDigest,
    iterations: config.kdfIterations,
    memorySize: memoryKiB,
    parallelism,
    hashLength: 32,
    outputType: "binary",
  });

  return toArrayBufferView(rawHash);
}

/**
 * Derives the Bitwarden Master Password Hash sent to the server for authentication.
 * PBKDF2-SHA256(password = masterKey, salt = UTF8(masterPassword), iterations = 1, dkLen = 32) -> Base64
 */
export async function deriveMasterPasswordHash(
  masterKey: Uint8Array,
  masterPassword: string,
  iterations: 1 | 2 = 1,
): Promise<string> {
  if (masterKey.byteLength !== 32) {
    throw new Error("Master key must be 32 bytes");
  }
  const hashBytes = await pbkdf2Sha256(masterKey, utf8ToBytes(masterPassword), iterations, 32);
  return bytesToBase64(hashBytes);
}

/**
 * Raw RFC 5869 HKDF-Expand (SHA-256) WITHOUT HKDF-Extract, matching Bitwarden's stretchKey().
 * For outputSize <= 32, HKDF-Expand(PRK, info, 32) = HMAC-SHA256(PRK, info || 0x01).
 */
export async function hkdfExpandSha256(
  prk: Uint8Array,
  info: Uint8Array,
  outputLengthBytes = 32,
): Promise<Uint8Array<ArrayBuffer>> {
  if (outputLengthBytes <= 0 || outputLengthBytes > 255 * 32) {
    throw new Error("Invalid HKDF-Expand output length");
  }

  const hmacKey = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(prk),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );

  const hashLen = 32;
  const blocks = Math.ceil(outputLengthBytes / hashLen);
  const okm = new Uint8Array(new ArrayBuffer(blocks * hashLen));
  let prevBlock: Uint8Array = new Uint8Array(0);

  for (let i = 1; i <= blocks; i++) {
    const input = concatBytes(prevBlock, info, new Uint8Array([i]));
    const block = new Uint8Array(await crypto.subtle.sign("HMAC", hmacKey, input));
    okm.set(block, (i - 1) * hashLen);
    prevBlock = block;
  }

  return toArrayBufferView(okm.subarray(0, outputLengthBytes));
}

/**
 * Stretches the 32-byte Master Key into a 64-byte SymmetricKey (32B encKey + 32B macKey)
 * using Bitwarden's exact HKDF-Expand("enc") and HKDF-Expand("mac") scheme.
 */
export async function stretchMasterKey(masterKey: Uint8Array): Promise<SymmetricKey> {
  if (masterKey.byteLength !== 32) {
    throw new Error("Master key must be 32 bytes");
  }
  const [encKey, macKey] = await Promise.all([
    hkdfExpandSha256(masterKey, utf8ToBytes("enc"), 32),
    hkdfExpandSha256(masterKey, utf8ToBytes("mac"), 32),
  ]);
  const raw = concatBytes(encKey, macKey);
  return { encKey, macKey, raw };
}
