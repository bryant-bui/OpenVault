import { argon2id } from "hash-wasm";
import {
  bytesToBase64,
  base64ToBytes,
  bytesToHex,
  concatBytes,
  randomBytes,
  toArrayBufferView,
  utf8ToBytes,
  zeroize,
} from "./bytes.js";
import {
  MacVerificationError,
  decryptString,
  encryptString,
  generateUserSymmetricKey,
  protectSymmetricKey,
  unprotectSymmetricKey,
} from "./cipher-string.js";
import { stretchMasterKey, validateKdfConfig } from "./kdf.js";
import { KdfType, type KdfConfig, type SymmetricKey } from "./types.js";

export const SAFE_MAX_FILE_BYTES = 5 * 1024 * 1024; // 5 MiB
export const SAFE_MAX_THUMB_BYTES = 128 * 1024; // 128 KiB
export const OVSF_MAGIC = new Uint8Array([0x4f, 0x56, 0x53, 0x46]); // "OVSF"
export const OVSF_VERSION = 1;
export const OVSF_SUITE_AES256_GCM = 1;
export const OVSF_HEADER_BYTES = 18;
export const OVSF_IV_BYTES = 12;
export const OVSF_TAG_BYTES = 16;
export const OVSF_OVERHEAD_BYTES = OVSF_HEADER_BYTES + OVSF_TAG_BYTES; // 34 bytes

export const SAFE_PIN_LENGTH = 6;
export const SAFE_COOLDOWN_AFTER_FAILS = 5;
export const SAFE_COOLDOWN_MS = 15 * 60 * 1000;
export const SAFE_MAX_TOTAL_FAILS = 15;
export const SAFE_DEFAULT_AUTO_LOCK_MINUTES = 3;

export const DEFAULT_SAFE_KDF_CONFIG: KdfConfig = {
  kdf: KdfType.Argon2id,
  kdfIterations: 3,
  kdfMemory: 64,
  kdfParallelism: 4,
};

export const DEFAULT_RECOVERY_KDF_ITERATIONS = 100_000;
export const SAFE_RECOVERY_KEY_BYTES = 20; // 160-bit entropy -> 32 Crockford Base32 chars

export const SAFE_DOCUMENT_CATEGORIES = [
  "id_card",
  "passport",
  "driver_license",
  "medical",
  "financial",
  "photo",
  "other",
] as const;

export type SafeDocumentCategory = (typeof SAFE_DOCUMENT_CATEGORIES)[number];

export interface SafeItemMetadata {
  v: 1;
  title: string;
  fileName: string;
  mimeType: string;
  category: SafeDocumentCategory;
  folderName?: string | null;
  notes?: string | null;
  fileKeyB64: string;
  thumbKeyB64?: string | null;
  originalSize: number;
  originalSha256: string;
  expiresAt?: string | null;
}

export interface ProtectedSafePinKey {
  saltB64: string;
  kdfConfig: KdfConfig;
  encryptedSafeKey: string;
}

export interface ProtectedSafeRecoveryKey {
  saltB64: string;
  iterations: number;
  encryptedSafeKey: string;
}

export class InvalidOvsfEnvelopeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "InvalidOvsfEnvelopeError";
  }
}

const CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

export function validateSafePin(pin: string): void {
  if (typeof pin !== "string" || !/^\d{6}$/.test(pin)) {
    throw new Error("Vault+ PIN must be exactly 6 numeric digits");
  }
}

export function generateSafeKey(): SymmetricKey {
  return generateUserSymmetricKey();
}

export function generateFileKey(): Uint8Array<ArrayBuffer> {
  return randomBytes(32);
}

export function encodeCrockfordBase32(bytes: Uint8Array): string {
  if (bytes.byteLength === 0) {
    throw new Error("Cannot encode empty buffer");
  }
  let bits = 0;
  let value = 0;
  let out = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    value = (value << 8) | (bytes[i] ?? 0);
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      out += CROCKFORD_ALPHABET[(value >>> bits) & 0x1f]!;
    }
  }
  if (bits > 0) {
    out += CROCKFORD_ALPHABET[(value << (5 - bits)) & 0x1f]!;
  }
  return out;
}

export function decodeCrockfordBase32(input: string): Uint8Array<ArrayBuffer> {
  const normalized = input
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, "")
    .replace(/[IL]/g, "1")
    .replace(/O/g, "0");

  if (normalized.length === 0) {
    throw new Error("Recovery key cannot be empty");
  }

  let bits = 0;
  let value = 0;
  const output: number[] = [];

  for (let i = 0; i < normalized.length; i++) {
    const ch = normalized[i]!;
    const idx = CROCKFORD_ALPHABET.indexOf(ch);
    if (idx === -1) {
      throw new Error(`Invalid recovery key character: ${ch}`);
    }
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      output.push((value >>> bits) & 0xff);
    }
  }

  const out = new Uint8Array(new ArrayBuffer(output.length));
  out.set(output);
  return out;
}

export function formatRecoveryKey(rawBytes: Uint8Array): string {
  if (rawBytes.byteLength !== SAFE_RECOVERY_KEY_BYTES) {
    throw new Error(`Recovery key must be ${SAFE_RECOVERY_KEY_BYTES} bytes`);
  }
  const encoded = encodeCrockfordBase32(rawBytes); // 32 chars
  const groups: string[] = [];
  for (let i = 0; i < encoded.length; i += 4) {
    groups.push(encoded.slice(i, i + 4));
  }
  return `OVRK-${groups.join("-")}`;
}

export function generateRecoveryKey(): {
  formatted: string;
  rawBytes: Uint8Array<ArrayBuffer>;
} {
  const rawBytes = randomBytes(SAFE_RECOVERY_KEY_BYTES);
  return {
    formatted: formatRecoveryKey(rawBytes),
    rawBytes,
  };
}

export function parseRecoveryKey(input: string): Uint8Array<ArrayBuffer> {
  if (typeof input !== "string") {
    throw new Error("Recovery key must be a string");
  }
  let cleaned = input.trim().toUpperCase().replace(/[\s-]+/g, "");
  if (cleaned.startsWith("OVRK")) {
    cleaned = cleaned.slice(4);
  }
  if (cleaned.length !== 32) {
    throw new Error("Recovery key must contain 32 Crockford Base32 characters");
  }
  const decoded = decodeCrockfordBase32(cleaned);
  if (decoded.byteLength !== SAFE_RECOVERY_KEY_BYTES) {
    throw new Error("Decoded recovery key length mismatch");
  }
  return decoded;
}

async function pbkdf2Sha256Raw(
  passwordBytes: Uint8Array,
  saltBytes: Uint8Array,
  iterations: number,
  dkLen = 32,
): Promise<Uint8Array<ArrayBuffer>> {
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(passwordBytes),
    { name: "PBKDF2" },
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: toArrayBufferView(saltBytes),
      iterations,
    },
    keyMaterial,
    dkLen * 8,
  );
  return new Uint8Array(bits);
}

async function deriveSafePinWrappingKey(
  pin: string,
  saltBytes: Uint8Array,
  kdfConfig: KdfConfig,
): Promise<SymmetricKey> {
  validateSafePin(pin);
  validateKdfConfig(kdfConfig);
  if (saltBytes.byteLength !== 16) {
    throw new Error(`Passcode salt must be 16 bytes, got ${saltBytes.byteLength}`);
  }

  const pinBytes = utf8ToBytes(pin);
  let derived32: Uint8Array<ArrayBuffer>;

  try {
    if (kdfConfig.kdf === KdfType.Argon2id) {
      const rawHash = await argon2id({
        password: pinBytes,
        salt: toArrayBufferView(saltBytes),
        iterations: kdfConfig.kdfIterations,
        memorySize: (kdfConfig.kdfMemory ?? 64) * 1024,
        parallelism: kdfConfig.kdfParallelism ?? 4,
        hashLength: 32,
        outputType: "binary",
      });
      derived32 = toArrayBufferView(rawHash);
    } else {
      derived32 = await pbkdf2Sha256Raw(pinBytes, saltBytes, kdfConfig.kdfIterations, 32);
    }
  } finally {
    zeroize(pinBytes);
  }

  try {
    return await stretchMasterKey(derived32);
  } finally {
    zeroize(derived32);
  }
}

export async function protectSafeKeyWithPin(
  safeKey: SymmetricKey,
  pin: string,
  kdfConfig: KdfConfig = DEFAULT_SAFE_KDF_CONFIG,
  explicitSalt?: Uint8Array,
  explicitIv?: Uint8Array,
): Promise<ProtectedSafePinKey> {
  const salt = explicitSalt ? toArrayBufferView(explicitSalt) : randomBytes(16);
  const wrappingKey = await deriveSafePinWrappingKey(pin, salt, kdfConfig);
  try {
    const encryptedSafeKey = await protectSymmetricKey(safeKey, wrappingKey, explicitIv);
    return {
      saltB64: bytesToBase64(salt),
      kdfConfig,
      encryptedSafeKey,
    };
  } finally {
    zeroize(wrappingKey.encKey, wrappingKey.macKey, wrappingKey.raw);
  }
}

export async function unprotectSafeKeyWithPin(
  encryptedSafeKey: string,
  pin: string,
  saltB64: string,
  kdfConfig: KdfConfig,
): Promise<SymmetricKey> {
  const salt = base64ToBytes(saltB64);
  const wrappingKey = await deriveSafePinWrappingKey(pin, salt, kdfConfig);
  try {
    return await unprotectSymmetricKey(encryptedSafeKey, wrappingKey);
  } finally {
    zeroize(wrappingKey.encKey, wrappingKey.macKey, wrappingKey.raw);
  }
}

async function deriveRecoveryWrappingKey(
  recoveryKeyInput: string | Uint8Array,
  saltBytes: Uint8Array,
  iterations: number,
): Promise<SymmetricKey> {
  if (saltBytes.byteLength !== 16) {
    throw new Error(`Recovery salt must be 16 bytes, got ${saltBytes.byteLength}`);
  }
  if (!Number.isInteger(iterations) || iterations < 1_000) {
    throw new Error(`Invalid recovery KDF iterations: ${iterations}`);
  }

  const rawRecovery =
    typeof recoveryKeyInput === "string"
      ? parseRecoveryKey(recoveryKeyInput)
      : toArrayBufferView(recoveryKeyInput);

  if (rawRecovery.byteLength !== SAFE_RECOVERY_KEY_BYTES) {
    zeroize(rawRecovery);
    throw new Error(`Recovery key must be ${SAFE_RECOVERY_KEY_BYTES} bytes`);
  }

  let derived32: Uint8Array<ArrayBuffer>;
  try {
    derived32 = await pbkdf2Sha256Raw(rawRecovery, saltBytes, iterations, 32);
  } finally {
    zeroize(rawRecovery);
  }

  try {
    return await stretchMasterKey(derived32);
  } finally {
    zeroize(derived32);
  }
}

export async function protectSafeKeyWithRecoveryKey(
  safeKey: SymmetricKey,
  recoveryKeyInput: string | Uint8Array,
  iterations = DEFAULT_RECOVERY_KDF_ITERATIONS,
  explicitSalt?: Uint8Array,
  explicitIv?: Uint8Array,
): Promise<ProtectedSafeRecoveryKey> {
  const salt = explicitSalt ? toArrayBufferView(explicitSalt) : randomBytes(16);
  const wrappingKey = await deriveRecoveryWrappingKey(recoveryKeyInput, salt, iterations);
  try {
    const encryptedSafeKey = await protectSymmetricKey(safeKey, wrappingKey, explicitIv);
    return {
      saltB64: bytesToBase64(salt),
      iterations,
      encryptedSafeKey,
    };
  } finally {
    zeroize(wrappingKey.encKey, wrappingKey.macKey, wrappingKey.raw);
  }
}

export async function unprotectSafeKeyWithRecoveryKey(
  encryptedSafeKey: string,
  recoveryKeyInput: string | Uint8Array,
  saltB64: string,
  iterations: number,
): Promise<SymmetricKey> {
  const salt = base64ToBytes(saltB64);
  const wrappingKey = await deriveRecoveryWrappingKey(recoveryKeyInput, salt, iterations);
  try {
    return await unprotectSymmetricKey(encryptedSafeKey, wrappingKey);
  } finally {
    zeroize(wrappingKey.encKey, wrappingKey.macKey, wrappingKey.raw);
  }
}

export async function encryptSafeMetadata(
  metadata: SafeItemMetadata,
  safeKey: SymmetricKey,
  explicitIv?: Uint8Array,
): Promise<string> {
  if (metadata.v !== 1 || !metadata.title.trim() || !metadata.fileKeyB64) {
    throw new Error("Invalid SafeItemMetadata payload");
  }
  return encryptString(JSON.stringify(metadata), safeKey, explicitIv);
}

export async function decryptSafeMetadata(
  cipherString: string,
  safeKey: SymmetricKey,
): Promise<SafeItemMetadata> {
  const raw = await decryptString(cipherString, safeKey);
  const parsed = JSON.parse(raw) as SafeItemMetadata;
  if (!parsed || parsed.v !== 1 || typeof parsed.fileKeyB64 !== "string") {
    throw new Error("Malformed decrypted SafeItemMetadata");
  }
  return parsed;
}

export type OvsfBlobKind = "blob" | "thumb";

export function buildOvsfAad(itemId: string, kind: OvsfBlobKind = "blob"): Uint8Array<ArrayBuffer> {
  const cleanId = itemId.trim();
  if (!cleanId) {
    throw new Error("itemId is required for OVSF AAD binding");
  }
  return utf8ToBytes(kind === "thumb" ? `OVSF:v1:thumb:${cleanId}` : `OVSF:v1:${cleanId}`);
}

export async function encryptSafeFileBinary(
  plaintext: Uint8Array,
  fileKey: Uint8Array,
  itemId: string,
  kind: OvsfBlobKind = "blob",
  explicitIv?: Uint8Array,
): Promise<Uint8Array<ArrayBuffer>> {
  if (fileKey.byteLength !== 32) {
    throw new Error(`FileKey must be 32 bytes, got ${fileKey.byteLength}`);
  }
  const maxPlain = kind === "thumb" ? SAFE_MAX_THUMB_BYTES : SAFE_MAX_FILE_BYTES;
  if (plaintext.byteLength > maxPlain) {
    throw new Error(`File exceeds maximum allowed size (${maxPlain} bytes)`);
  }

  const iv = explicitIv ? toArrayBufferView(explicitIv) : randomBytes(OVSF_IV_BYTES);
  if (iv.byteLength !== OVSF_IV_BYTES) {
    throw new Error(`OVSF IV must be ${OVSF_IV_BYTES} bytes, got ${iv.byteLength}`);
  }

  const aad = buildOvsfAad(itemId, kind);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(fileKey),
    { name: "AES-GCM" },
    false,
    ["encrypt"],
  );

  const ctAndTag = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: "AES-GCM", iv, additionalData: aad, tagLength: 128 },
      cryptoKey,
      toArrayBufferView(plaintext),
    ),
  );

  const header = new Uint8Array(new ArrayBuffer(OVSF_HEADER_BYTES));
  header.set(OVSF_MAGIC, 0);
  header[4] = OVSF_VERSION;
  header[5] = OVSF_SUITE_AES256_GCM;
  header.set(iv, 6);

  return concatBytes(header, ctAndTag);
}

export async function decryptSafeFileBinary(
  envelope: Uint8Array,
  fileKey: Uint8Array,
  itemId: string,
  kind: OvsfBlobKind = "blob",
): Promise<Uint8Array<ArrayBuffer>> {
  if (fileKey.byteLength !== 32) {
    throw new Error(`FileKey must be 32 bytes, got ${fileKey.byteLength}`);
  }
  if (envelope.byteLength < OVSF_OVERHEAD_BYTES) {
    throw new InvalidOvsfEnvelopeError(
      `OVSF envelope too short: ${envelope.byteLength} bytes`,
    );
  }
  for (let i = 0; i < OVSF_MAGIC.length; i++) {
    if (envelope[i] !== OVSF_MAGIC[i]) {
      throw new InvalidOvsfEnvelopeError("Invalid OVSF magic header");
    }
  }
  if (envelope[4] !== OVSF_VERSION) {
    throw new InvalidOvsfEnvelopeError(`Unsupported OVSF version: ${envelope[4]}`);
  }
  if (envelope[5] !== OVSF_SUITE_AES256_GCM) {
    throw new InvalidOvsfEnvelopeError(`Unsupported OVSF suite: ${envelope[5]}`);
  }

  const iv = toArrayBufferView(envelope.subarray(6, OVSF_HEADER_BYTES));
  const ctAndTag = toArrayBufferView(envelope.subarray(OVSF_HEADER_BYTES));
  const aad = buildOvsfAad(itemId, kind);

  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    toArrayBufferView(fileKey),
    { name: "AES-GCM" },
    false,
    ["decrypt"],
  );

  try {
    const plain = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv, additionalData: aad, tagLength: 128 },
      cryptoKey,
      ctAndTag,
    );
    return new Uint8Array(plain);
  } catch {
    throw new MacVerificationError("OVSF GCM authentication tag verification failed");
  }
}

export async function sha256HexBytes(data: Uint8Array): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", toArrayBufferView(data));
  return bytesToHex(new Uint8Array(digest));
}
