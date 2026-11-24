import crypto from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import zlib from "node:zlib";
import { describe, expect, it } from "vitest";
import {
  CipherType,
  DEFAULT_ARGON2ID_CONFIG,
  DEFAULT_PBKDF2_CONFIG,
  FieldType,
  InvalidCipherStringError,
  KdfType,
  MacVerificationError,
  base64ToBytes,
  bytesToBase64,
  bytesToHex,
  createPasskeyCredential,
  decryptBytes,
  decryptCipher,
  decryptFolder,
  decryptString,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptBytes,
  encryptCipher,
  encryptFolder,
  encryptString,
  estimatePasswordStrength,
  extractVaultTextFromZip,
  generatePassphrase,
  generatePassword,
  generateProtectedRsaKeyPair,
  generateTotp,
  generateUserSymmetricKey,
  hexToBytes,
  hkdfExpandSha256,
  p1363ToDer,
  parseTotpInput,
  parseUniversalVaultImport,
  prepareBitwardenImport,
  prepareUniversalVaultImport,
  protectSymmetricKey,
  protectUserKeyWithPin,
  signPasskeyAssertion,
  splitSymmetricKey,
  stretchMasterKey,
  unprotectSymmetricKey,
  unprotectUserKeyWithPin,
  utf8ToBytes,
} from "../src/index.js";

const vectorsPath = resolve(import.meta.dirname, "../../../spec/test-vectors.json");

interface TestVectors {
  pbkdf2: {
    email: string;
    masterPassword: string;
    iterations: number;
    expectedMasterKeyHex: string;
    expectedMasterPasswordHashB64: string;
    expectedLocalMasterPasswordHashB64: string;
    expectedStretchedEncKeyHex: string;
    expectedStretchedMacKeyHex: string;
  };
  argon2id: {
    email: string;
    masterPassword: string;
    iterations: number;
    memoryMiB: number;
    parallelism: number;
    expectedSaltHex: string;
    expectedMasterKeyHex: string;
    expectedMasterPasswordHashB64: string;
  };
  cipherStringType2: {
    encKeyHex: string;
    macKeyHex: string;
    ivHex: string;
    plaintext: string;
    expectedCipherString: string;
  };
  totpRfc6238: {
    secretBase32: string;
    digits: number;
    period: number;
    algorithm: "SHA1" | "SHA256" | "SHA512";
    cases: { unixSeconds: number; expectedCode: string }[];
  }[];
}

function loadVectors(): TestVectors {
  return JSON.parse(readFileSync(vectorsPath, "utf8")) as TestVectors;
}

describe("@openvault/crypto-core — Cross-Platform Spec Test Vectors", () => {
  it("matches Bitwarden PBKDF2-SHA256, MasterPasswordHash, and raw HKDF-Expand vectors", async () => {
    const v = loadVectors().pbkdf2;

    const masterKey = await deriveMasterKey(v.masterPassword, v.email, {
      kdf: KdfType.PBKDF2_SHA256,
      kdfIterations: v.iterations,
    });
    expect(bytesToHex(masterKey)).toBe(v.expectedMasterKeyHex);

    // Independently cross-check against Node.js native OpenSSL crypto
    const nodeMasterKey = crypto.pbkdf2Sync(
      v.masterPassword,
      v.email.trim().toLowerCase(),
      v.iterations,
      32,
      "sha256",
    );
    expect(bytesToHex(masterKey)).toBe(nodeMasterKey.toString("hex"));

    const mpHash = await deriveMasterPasswordHash(masterKey, v.masterPassword, 1);
    expect(mpHash).toBe(v.expectedMasterPasswordHashB64);

    const localHash = await deriveMasterPasswordHash(masterKey, v.masterPassword, 2);
    expect(localHash).toBe(v.expectedLocalMasterPasswordHashB64);

    const stretched = await stretchMasterKey(masterKey);
    expect(bytesToHex(stretched.encKey)).toBe(v.expectedStretchedEncKeyHex);
    expect(bytesToHex(stretched.macKey)).toBe(v.expectedStretchedMacKeyHex);

    // Independently verify raw HKDF-Expand (no extract!) via Node HMAC
    const expectedEnc = crypto
      .createHmac("sha256", nodeMasterKey)
      .update(Buffer.concat([Buffer.from("enc", "utf8"), Buffer.from([1])]))
      .digest("hex");
    const expectedMac = crypto
      .createHmac("sha256", nodeMasterKey)
      .update(Buffer.concat([Buffer.from("mac", "utf8"), Buffer.from([1])]))
      .digest("hex");
    expect(bytesToHex(stretched.encKey)).toBe(expectedEnc);
    expect(bytesToHex(stretched.macKey)).toBe(expectedMac);
  });

  it("matches Bitwarden Argon2id vector with SHA-256(lowercase(email)) salt", async () => {
    const v = loadVectors().argon2id;

    const saltHex = crypto
      .createHash("sha256")
      .update(v.email.trim().toLowerCase(), "utf8")
      .digest("hex");
    expect(saltHex).toBe(v.expectedSaltHex);

    const masterKey = await deriveMasterKey(v.masterPassword, v.email, {
      kdf: KdfType.Argon2id,
      kdfIterations: v.iterations,
      kdfMemory: v.memoryMiB,
      kdfParallelism: v.parallelism,
    });
    expect(bytesToHex(masterKey)).toBe(v.expectedMasterKeyHex);

    const mpHash = await deriveMasterPasswordHash(masterKey, v.masterPassword, 1);
    expect(mpHash).toBe(v.expectedMasterPasswordHashB64);
  });

  it("matches Bitwarden CipherString Type 2 (2.iv|ct|mac) vector and rejects tampering", async () => {
    const v = loadVectors().cipherStringType2;
    const rawKey = new Uint8Array(64);
    rawKey.set(hexToBytes(v.encKeyHex), 0);
    rawKey.set(hexToBytes(v.macKeyHex), 32);
    const symKey = splitSymmetricKey(rawKey);
    const iv = hexToBytes(v.ivHex);

    const encrypted = await encryptString(v.plaintext, symKey, iv);
    expect(encrypted).toBe(v.expectedCipherString);

    const decrypted = await decryptString(encrypted, symKey);
    expect(decrypted).toBe(v.plaintext);

    // Tamper 1 bit in ciphertext -> MUST throw MacVerificationError before AES decrypt
    const [prefixAndIv, ctB64, macB64] = encrypted.split("|") as [string, string, string];
    const ctBytes = base64ToBytes(ctB64);
    ctBytes[0] = (ctBytes[0] ?? 0) ^ 0x01;
    const tamperedCt = `${prefixAndIv}|${bytesToBase64(ctBytes)}|${macB64}`;
    await expect(decryptString(tamperedCt, symKey)).rejects.toThrow(MacVerificationError);

    // Tamper 1 bit in MAC -> MUST throw MacVerificationError
    const macBytes = base64ToBytes(macB64);
    macBytes[31] = (macBytes[31] ?? 0) ^ 0x80;
    const tamperedMac = `${prefixAndIv}|${ctB64}|${bytesToBase64(macBytes)}`;
    await expect(decryptString(tamperedMac, symKey)).rejects.toThrow(MacVerificationError);

    // Reject unsupported encryption type
    await expect(decryptString("0.AAAA|BBBB", symKey)).rejects.toThrow(InvalidCipherStringError);
  });

  it("matches official RFC 6238 TOTP test vectors (SHA1, SHA256, SHA512)", async () => {
    const groups = loadVectors().totpRfc6238;
    for (const group of groups) {
      const uri = `otpauth://totp/OpenVault:test?secret=${group.secretBase32}&algorithm=${group.algorithm}&digits=${group.digits}&period=${group.period}`;
      for (const tc of group.cases) {
        const res = await generateTotp(uri, tc.unixSeconds * 1000);
        expect(res.code).toBe(tc.expectedCode);
      }
    }
  });
});

describe("@openvault/crypto-core — Vault, RSA, Generator, Passkey", () => {
  it("protects & unprotects user symmetric key and generates RSA-2048 keypair", async () => {
    const masterKey = await deriveMasterKey("SuperSecret!2026", "User@OpenVault.io", {
      kdf: KdfType.PBKDF2_SHA256,
      kdfIterations: 100_000,
    });
    const stretched = await stretchMasterKey(masterKey);
    const userKey = generateUserSymmetricKey();

    const protectedKey = await protectSymmetricKey(userKey, stretched);
    expect(protectedKey.startsWith("2.")).toBe(true);

    const recovered = await unprotectSymmetricKey(protectedKey, stretched);
    expect(bytesToHex(recovered.raw)).toBe(bytesToHex(userKey.raw));

    const rsa = await generateProtectedRsaKeyPair(userKey);
    expect(rsa.publicKey.length).toBeGreaterThan(200);
    expect(rsa.encryptedPrivateKey.startsWith("2.")).toBe(true);
    const decryptedPkcs8 = await decryptBytes(rsa.encryptedPrivateKey, userKey);
    expect(decryptedPkcs8.byteLength).toBeGreaterThan(1000);
  });

  it("encrypts and decrypts complete Login cipher with TOTP, Passkey, and Custom Fields", async () => {
    const userKey = generateUserSymmetricKey();
    const passkey = await createPasskeyCredential({
      rpId: "github.com",
      rpName: "GitHub",
      userHandle: utf8ToBytes("user-handle-123"),
      userName: "alex@example.com",
      userDisplayName: "Alex",
    });

    const plainCipher = {
      type: CipherType.Login,
      name: "GitHub Personal",
      notes: "Recovery codes saved offline",
      favorite: true,
      login: {
        uris: [{ uri: "https://github.com/login", match: null }],
        username: "alex@example.com",
        password: "Tr0ub4dor&3!Complex",
        totp: "otpauth://totp/GitHub:alex?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
        fido2Credentials: [passkey.credential],
      },
      fields: [{ name: "PIN", value: "9182", type: FieldType.Hidden }],
    };

    const encrypted = await encryptCipher(plainCipher, userKey);
    expect(encrypted.name.startsWith("2.")).toBe(true);
    expect(encrypted.login?.password?.startsWith("2.")).toBe(true);
    expect(encrypted.login?.totp?.startsWith("2.")).toBe(true);
    expect(encrypted.login?.fido2Credentials?.[0]?.keyValue.startsWith("2.")).toBe(true);
    // Ensure plaintext never leaks into encrypted fields
    expect(JSON.stringify(encrypted)).not.toContain("Tr0ub4dor");
    expect(JSON.stringify(encrypted)).not.toContain("JBSWY3DPEHPK3PXP");

    const decrypted = await decryptCipher(encrypted, userKey);
    expect(decrypted.name).toBe(plainCipher.name);
    expect(decrypted.login?.password).toBe(plainCipher.login.password);
    expect(decrypted.login?.totp).toBe(plainCipher.login.totp);
    expect(decrypted.login?.fido2Credentials?.[0]?.keyValue).toBe(passkey.credential.keyValue);
    expect(decrypted.fields?.[0]?.value).toBe("9182");

    // Verify WebAuthn assertion signature with Node's ECDSA SPKI verifier!
    const clientDataJson = utf8ToBytes(
      JSON.stringify({
        type: "webauthn.get",
        challenge: "dGVzdC1jaGFsbGVuZ2U",
        origin: "https://github.com",
      }),
    );
    const assertion = await signPasskeyAssertion(
      decrypted.login!.fido2Credentials![0]!,
      clientDataJson,
    );
    const clientDataHash = crypto.createHash("sha256").update(clientDataJson).digest();
    const signedData = Buffer.concat([Buffer.from(assertion.authenticatorData), clientDataHash]);
    const pubKeyObj = crypto.createPublicKey({
      key: Buffer.from(passkey.publicKeySpki),
      format: "der",
      type: "spki",
    });
    const verified = crypto.verify(
      "sha256",
      signedData,
      pubKeyObj,
      Buffer.from(assertion.signatureDer),
    );
    expect(verified).toBe(true);
  });

  it("encrypts/decrypts folders and prepares Bitwarden JSON import", async () => {
    const userKey = generateUserSymmetricKey();
    const encFolder = await encryptFolder({ name: "Banking" }, userKey);
    expect(encFolder.name.startsWith("2.")).toBe(true);
    expect((await decryptFolder(encFolder, userKey)).name).toBe("Banking");

    const payload = await prepareBitwardenImport(
      {
        encrypted: false,
        folders: [{ id: "f-1", name: "Work" }],
        items: [
          {
            id: "old-id",
            folderId: "f-1",
            type: CipherType.Login,
            name: "Cloudflare",
            login: { username: "me@example.com", password: "pw" },
          },
        ],
      },
      userKey,
    );
    expect(payload.folders).toHaveLength(1);
    expect(payload.ciphers).toHaveLength(1);
    expect(payload.folderRelationships).toEqual([{ key: 0, value: 0 }]);
    expect(payload.ciphers[0]!.name.startsWith("2.")).toBe(true);
  });

  it("generates unbiased passwords, parses TOTP inputs, validates defaults, and converts P1363 to DER", async () => {
    expect(DEFAULT_PBKDF2_CONFIG.kdfIterations).toBe(600_000);
    expect(DEFAULT_ARGON2ID_CONFIG.kdfMemory).toBe(64);

    const pw = generatePassword({
      length: 24,
      lowercase: true,
      uppercase: true,
      digits: true,
      symbols: true,
      avoidAmbiguous: true,
    });
    expect(pw).toHaveLength(24);
    expect(/[lIO01]/.test(pw)).toBe(false);
    expect(estimatePasswordStrength(pw).score).toBe(4);

    const phrase = generatePassphrase({ wordCount: 4, separator: "-", capitalize: true, includeNumber: true });
    expect(phrase.split("-")).toHaveLength(4);
    expect(/\d{2}/.test(phrase)).toBe(true);

    const parsed = parseTotpInput("JBSW Y3DP EHPK 3PXP");
    expect(parsed.algorithm).toBe("SHA-1");
    expect(parsed.digits).toBe(6);

    // P1363 leading high-bit padding check (0x80 -> must prefix 0x00 in ASN.1 INTEGER)
    const rawSig = new Uint8Array(64).fill(0x80);
    const der = p1363ToDer(rawSig);
    expect(der[0]).toBe(0x30);
    expect(der[2]).toBe(0x02);
    expect(der[3]).toBe(33);
    expect(der[4]).toBe(0x00);

    // HKDF-Expand 64-byte multi-block sanity check
    const okm = await hkdfExpandSha256(new Uint8Array(32).fill(7), utf8ToBytes("multi"), 64);
    expect(okm.byteLength).toBe(64);
  });

  it("imports Proton Pass data.json (login + TOTP + passkey + card + note), Proton Pass CSV, and DEFLATE ZIP", async () => {
    const userKey = generateUserSymmetricKey();

    // 1. Proton Pass data.json
    const protonJson = JSON.stringify({
      version: "1.43.0",
      vaults: {
        "vault-personal": {
          name: "Personal",
          items: [
            {
              itemId: "i1",
              state: 1,
              pinned: true,
              data: {
                type: "login",
                metadata: { name: "GitHub", note: "Main dev account" },
                content: {
                  itemEmail: "me@example.com",
                  itemUsername: "testuser",
                  password: "ProtonSecret!99",
                  urls: ["https://github.com/login"],
                  totpUri: "otpauth://totp/GitHub:alex?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
                  passkeys: [
                    {
                      credentialId: "cred-123",
                      domain: "github.com",
                      rpName: "GitHub",
                      userName: "testuser",
                      content: "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg",
                      createTime: 1719000000,
                    },
                  ],
                },
                extraFields: [{ fieldName: "Recovery", type: "hidden", data: { content: "ABC-123" } }],
              },
            },
            {
              itemId: "i2",
              state: 1,
              data: {
                type: "creditCard",
                metadata: { name: "Visa Personal" },
                content: {
                  cardholderName: "ALEX MORGAN",
                  number: "4111222233334444",
                  verificationNumber: "321",
                  expirationDate: "12/28",
                  pin: "9988",
                },
              },
            },
            {
              itemId: "i3",
              state: 2, // Trashed -> must be skipped
              data: { type: "note", metadata: { name: "Deleted Note", note: "skip me" }, content: {} },
            },
          ],
        },
      },
    });

    const prepared = await prepareUniversalVaultImport(protonJson, userKey);
    expect(prepared.format).toBe("proton-pass-json");
    expect(prepared.skippedCount).toBe(1);
    expect(prepared.payload.folders).toHaveLength(1);
    expect(prepared.payload.ciphers).toHaveLength(2);
    expect(prepared.payload.folderRelationships).toEqual([
      { key: 0, value: 0 },
      { key: 1, value: 0 },
    ]);

    const decLogin = await decryptCipher(prepared.payload.ciphers[0]!, userKey);
    expect(decLogin.name).toBe("GitHub");
    expect(decLogin.favorite).toBe(true);
    expect(decLogin.login?.username).toBe("testuser");
    expect(decLogin.login?.password).toBe("ProtonSecret!99");
    expect(decLogin.login?.totp).toContain("JBSWY3DPEHPK3PXP");
    expect(decLogin.login?.fido2Credentials?.[0]?.rpId).toBe("github.com");
    expect(decLogin.fields?.find((f) => f.name === "Email")?.value).toBe("me@example.com");

    const decCard = await decryptCipher(prepared.payload.ciphers[1]!, userKey);
    expect(decCard.type).toBe(CipherType.Card);
    expect(decCard.card?.expMonth).toBe("12");
    expect(decCard.card?.expYear).toBe("2028");
    expect(decCard.fields?.find((f) => f.name === "PIN")?.value).toBe("9988");

    // 2. Proton Pass CSV with multiline note and BOM
    const csv = `\uFEFFtype,name,url,email,username,password,note,totp,createTime,modifyTime,vault\r\nlogin,Cloudflare,https://dash.cloudflare.com,alex@example.com,,CfPass#2026,"Line 1\nLine ""2""",JBSWY3DPEHPK3PXP,1700000000,1700000000,Personal\r\nnote,Backup Codes,,,,"" ,"Code A\nCode B",,1700000000,1700000000,Personal`;
    const parsedCsv = parseUniversalVaultImport(csv);
    expect(parsedCsv.format).toBe("proton-pass-csv");
    expect(parsedCsv.exportData.folders).toHaveLength(1);
    expect(parsedCsv.exportData.items).toHaveLength(2);
    expect(parsedCsv.exportData.items![0]!.login?.username).toBe("alex@example.com");
    expect(parsedCsv.exportData.items![0]!.notes).toBe('Line 1\nLine "2"');
    expect(parsedCsv.exportData.items![1]!.type).toBe(CipherType.SecureNote);

    // 3. Real DEFLATE-compressed ZIP archive containing `Proton Pass/data.json`
    const entryName = Buffer.from("Proton Pass/data.json", "utf8");
    const rawData = Buffer.from(protonJson, "utf8");
    const deflated = zlib.deflateRawSync(rawData);

    const lh = Buffer.alloc(30 + entryName.length);
    lh.writeUInt32LE(0x04034b50, 0);
    lh.writeUInt16LE(20, 4);
    lh.writeUInt16LE(8, 8); // DEFLATE
    lh.writeUInt32LE(deflated.length, 18);
    lh.writeUInt32LE(rawData.length, 22);
    lh.writeUInt16LE(entryName.length, 26);
    entryName.copy(lh, 30);

    const cdOffset = lh.length + deflated.length;
    const cd = Buffer.alloc(46 + entryName.length);
    cd.writeUInt32LE(0x02014b50, 0);
    cd.writeUInt16LE(20, 4);
    cd.writeUInt16LE(20, 6);
    cd.writeUInt16LE(8, 10); // DEFLATE
    cd.writeUInt32LE(deflated.length, 20);
    cd.writeUInt32LE(rawData.length, 24);
    cd.writeUInt16LE(entryName.length, 28);
    cd.writeUInt32LE(0, 42);
    entryName.copy(cd, 46);

    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0);
    eocd.writeUInt16LE(1, 8);
    eocd.writeUInt16LE(1, 10);
    eocd.writeUInt32LE(cd.length, 12);
    eocd.writeUInt32LE(cdOffset, 16);

    const zipBytes = Buffer.concat([lh, deflated, cd, eocd]);
    const extracted = await extractVaultTextFromZip(new Uint8Array(zipBytes));
    expect(extracted.filename).toBe("Proton Pass/data.json");
    expect(JSON.parse(extracted.text).version).toBe("1.43.0");
  });

  it("wraps and unwraps UserSymmetricKey with Zero-Knowledge PIN and rejects wrong PIN", async () => {
    const userKey = generateUserSymmetricKey();
    const record = await protectUserKeyWithPin(userKey, "246810", 100_000);
    expect(record.protectedKey.startsWith("2.")).toBe(true);

    const recovered = await unprotectUserKeyWithPin(record, "246810");
    expect(bytesToHex(recovered.raw)).toBe(bytesToHex(userKey.raw));

    await expect(unprotectUserKeyWithPin(record, "999999")).rejects.toThrow(MacVerificationError);
  });
});
