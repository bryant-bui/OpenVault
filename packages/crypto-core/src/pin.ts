import { base64ToBytes, bytesToBase64, randomBytes, utf8ToBytes } from "./bytes.js";
import { protectSymmetricKey, unprotectSymmetricKey } from "./cipher-string.js";
import { stretchMasterKey } from "./kdf.js";
import type { SymmetricKey } from "./types.js";

export const DEFAULT_PIN_KDF_ITERATIONS = 600_000;
export const MAX_PIN_ATTEMPTS = 5;

export interface PinProtectedRecord {
  saltB64: string;
  iterations: number;
  protectedKey: string;
}

async function derivePinStretchedKey(
  pin: string,
  saltBytes: Uint8Array<ArrayBuffer>,
  iterations: number,
): Promise<SymmetricKey> {
  if (!pin || pin.length < 4 || pin.length > 32) {
    throw new Error("Mã PIN phải dài từ 4 đến 32 ký tự.");
  }

  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    utf8ToBytes(pin),
    { name: "PBKDF2" },
    false,
    ["deriveBits"],
  );

  const derived = new Uint8Array(
    await crypto.subtle.deriveBits(
      {
        name: "PBKDF2",
        hash: "SHA-256",
        salt: saltBytes,
        iterations,
      },
      keyMaterial,
      256,
    ),
  );

  return stretchMasterKey(derived);
}

/**
 * Wraps the 64-byte UserSymmetricKey under a PBKDF2-SHA256 (600,000 iter) key
 * derived from the user's PIN + random 16-byte salt. The PIN itself is never stored.
 */
export async function protectUserKeyWithPin(
  userKey: SymmetricKey,
  pin: string,
  iterations = DEFAULT_PIN_KDF_ITERATIONS,
): Promise<PinProtectedRecord> {
  const salt = randomBytes(16);
  const stretchedPinKey = await derivePinStretchedKey(pin, salt, iterations);
  const protectedKey = await protectSymmetricKey(userKey, stretchedPinKey);
  return {
    saltB64: bytesToBase64(salt),
    iterations,
    protectedKey,
  };
}

/**
 * Unwraps the UserSymmetricKey using the user's PIN.
 * Throws MacVerificationError if the PIN is incorrect.
 */
export async function unprotectUserKeyWithPin(
  record: PinProtectedRecord,
  pin: string,
): Promise<SymmetricKey> {
  const salt = base64ToBytes(record.saltB64);
  const stretchedPinKey = await derivePinStretchedKey(pin, salt, record.iterations);
  return unprotectSymmetricKey(record.protectedKey, stretchedPinKey);
}
