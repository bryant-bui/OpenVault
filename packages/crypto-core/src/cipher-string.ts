import {
  base64ToBytes,
  bytesToBase64,
  bytesToUtf8,
  concatBytes,
  constantTimeEqual,
  randomBytes,
  toArrayBufferView,
  utf8ToBytes,
} from "./bytes.js";
import { EncryptionType, type SymmetricKey } from "./types.js";

export class InvalidCipherStringError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidCipherStringError";
  }
}

export class MacVerificationError extends Error {
  constructor(message = "CipherString MAC verification failed") {
    super(message);
    this.name = "MacVerificationError";
  }
}

export function splitSymmetricKey(rawKey: Uint8Array): SymmetricKey {
  if (rawKey.byteLength !== 64) {
    throw new Error(`SymmetricKey must be 64 bytes, got ${rawKey.byteLength}`);
  }
  const raw = toArrayBufferView(rawKey);
  const encKey = toArrayBufferView(raw.subarray(0, 32));
  const macKey = toArrayBufferView(raw.subarray(32, 64));
  return { encKey, macKey, raw };
}

export function generateUserSymmetricKey(): SymmetricKey {
  return splitSymmetricKey(randomBytes(64));
}

async function computeHmacSha256(
  macKey: Uint8Array,
  data: Uint8Array,
): Promise<Uint8Array<ArrayBuffer>> {
  const key = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(macKey),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, toArrayBufferView(data));
  return new Uint8Array(mac);
}

/**
 * Encrypts raw bytes into a Bitwarden Type 2 CipherString:
 *   2.<Base64_IV>|<Base64_Ciphertext>|<Base64_MAC>
 * Optional deterministic IV is accepted strictly for test vector verification.
 */
export async function encryptBytes(
  plaintext: Uint8Array,
  key: SymmetricKey,
  explicitIv?: Uint8Array,
): Promise<string> {
  const iv = explicitIv ? toArrayBufferView(explicitIv) : randomBytes(16);
  if (iv.byteLength !== 16) {
    throw new Error("AES-CBC IV must be 16 bytes");
  }

  const aesKey = await crypto.subtle.importKey(
    "raw",
    key.encKey,
    { name: "AES-CBC" },
    false,
    ["encrypt"],
  );

  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-CBC", iv },
      aesKey,
      toArrayBufferView(plaintext),
    ),
  );

  const macInput = concatBytes(iv, ciphertext);
  const mac = await computeHmacSha256(key.macKey, macInput);

  return `${EncryptionType.AesCbc256_HmacSha256_B64}.${bytesToBase64(iv)}|${bytesToBase64(ciphertext)}|${bytesToBase64(mac)}`;
}

export async function encryptString(
  plaintext: string,
  key: SymmetricKey,
  explicitIv?: Uint8Array,
): Promise<string> {
  return encryptBytes(utf8ToBytes(plaintext), key, explicitIv);
}

export interface ParsedType2CipherString {
  encType: EncryptionType.AesCbc256_HmacSha256_B64;
  iv: Uint8Array<ArrayBuffer>;
  ciphertext: Uint8Array<ArrayBuffer>;
  mac: Uint8Array<ArrayBuffer>;
}

export function parseType2CipherString(cipherString: string): ParsedType2CipherString {
  if (typeof cipherString !== "string" || !cipherString.startsWith("2.")) {
    throw new InvalidCipherStringError("Only Bitwarden Encryption Type 2 (2.iv|ct|mac) is supported");
  }
  const payload = cipherString.slice(2);
  const parts = payload.split("|");
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) {
    throw new InvalidCipherStringError("Malformed Type 2 CipherString");
  }

  let iv: Uint8Array<ArrayBuffer>;
  let ciphertext: Uint8Array<ArrayBuffer>;
  let mac: Uint8Array<ArrayBuffer>;
  try {
    iv = base64ToBytes(parts[0]);
    ciphertext = base64ToBytes(parts[1]);
    mac = base64ToBytes(parts[2]);
  } catch {
    throw new InvalidCipherStringError("Invalid Base64 in CipherString");
  }

  if (iv.byteLength !== 16) {
    throw new InvalidCipherStringError(`Invalid IV length: ${iv.byteLength}`);
  }
  if (mac.byteLength !== 32) {
    throw new InvalidCipherStringError(`Invalid MAC length: ${mac.byteLength}`);
  }
  if (ciphertext.byteLength < 16 || ciphertext.byteLength % 16 !== 0) {
    throw new InvalidCipherStringError(`Invalid AES-CBC ciphertext length: ${ciphertext.byteLength}`);
  }

  return {
    encType: EncryptionType.AesCbc256_HmacSha256_B64,
    iv,
    ciphertext,
    mac,
  };
}

/**
 * Decrypts a Bitwarden Type 2 CipherString using strict Encrypt-then-MAC verification.
 * Always verifies HMAC-SHA256 in constant time BEFORE invoking AES-CBC decrypt.
 */
export async function decryptBytes(
  cipherString: string,
  key: SymmetricKey,
): Promise<Uint8Array<ArrayBuffer>> {
  const { iv, ciphertext, mac } = parseType2CipherString(cipherString);

  const expectedMac = await computeHmacSha256(key.macKey, concatBytes(iv, ciphertext));
  if (!constantTimeEqual(expectedMac, mac)) {
    throw new MacVerificationError();
  }

  const aesKey = await crypto.subtle.importKey(
    "raw",
    key.encKey,
    { name: "AES-CBC" },
    false,
    ["decrypt"],
  );

  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-CBC", iv },
    aesKey,
    ciphertext,
  );
  return new Uint8Array(plaintext);
}

export async function decryptString(
  cipherString: string,
  key: SymmetricKey,
): Promise<string> {
  const bytes = await decryptBytes(cipherString, key);
  return bytesToUtf8(bytes);
}

export async function protectSymmetricKey(
  userSymmetricKey: SymmetricKey,
  stretchedMasterKey: SymmetricKey,
  explicitIv?: Uint8Array,
): Promise<string> {
  return encryptBytes(userSymmetricKey.raw, stretchedMasterKey, explicitIv);
}

export async function unprotectSymmetricKey(
  protectedKeyCipherString: string,
  stretchedMasterKey: SymmetricKey,
): Promise<SymmetricKey> {
  const raw = await decryptBytes(protectedKeyCipherString, stretchedMasterKey);
  return splitSymmetricKey(raw);
}

export interface ProtectedRsaKeyPair {
  /** Base64 SPKI public key (Bitwarden profile.keys.publicKey) */
  publicKey: string;
  /** Type 2 CipherString of PKCS#8 private key encrypted with userSymmetricKey */
  encryptedPrivateKey: string;
}

/**
 * Generates a Bitwarden-compatible RSA-2048 OAEP-SHA1 user keypair.
 */
export async function generateProtectedRsaKeyPair(
  userSymmetricKey: SymmetricKey,
): Promise<ProtectedRsaKeyPair> {
  const generated = await crypto.subtle.generateKey(
    {
      name: "RSA-OAEP",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-1",
    },
    true,
    ["encrypt", "decrypt"],
  );
  if (!("publicKey" in generated)) {
    throw new Error("Expected RSA CryptoKeyPair");
  }

  const [spki, pkcs8] = await Promise.all([
    crypto.subtle.exportKey("spki", generated.publicKey),
    crypto.subtle.exportKey("pkcs8", generated.privateKey),
  ]);
  if (!(spki instanceof ArrayBuffer) || !(pkcs8 instanceof ArrayBuffer)) {
    throw new Error("Expected ArrayBuffer RSA key exports");
  }

  const encryptedPrivateKey = await encryptBytes(new Uint8Array(pkcs8), userSymmetricKey);
  return {
    publicKey: bytesToBase64(new Uint8Array(spki)),
    encryptedPrivateKey,
  };
}
