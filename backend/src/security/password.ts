/**
 * Server-side password hash stretching & constant-time verification
 * specifically tuned for Cloudflare Workers Web Crypto limits:
 *   - Web Crypto PBKDF2 hard cap in workerd: 100,000 iterations.
 *   - Client ALREADY ran heavy Argon2id (64MiB) or PBKDF2-SHA256 (600,000 iter)
 *     to produce the high-entropy 256-bit `masterPasswordHash`.
 *   - Server binds it with a per-user random 16-byte salt + Worker secret pepper
 *     and compares using `crypto.subtle.timingSafeEqual` over fixed 32-byte digests.
 */

const encoder = new TextEncoder();

export const WORKER_MAX_PBKDF2_ITERATIONS = 100_000;

function toBase64(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]!);
  }
  return btoa(bin);
}

function fromBase64(b64: string): Uint8Array<ArrayBuffer> {
  const bin = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out;
}

export function generateSaltB64(): string {
  const salt = new Uint8Array(new ArrayBuffer(16));
  crypto.getRandomValues(salt);
  return toBase64(salt);
}

export async function sha256Hex(value: string): Promise<string> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value)));
  let hex = "";
  for (let i = 0; i < digest.length; i++) {
    hex += digest[i]!.toString(16).padStart(2, "0");
  }
  return hex;
}

export function generateOpaqueToken(byteLength = 32): string {
  const buf = new Uint8Array(new ArrayBuffer(byteLength));
  crypto.getRandomValues(buf);
  return toBase64(buf).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function hashServerPassword(
  clientMasterPasswordHash: string,
  saltB64: string,
  pepper: string,
  iterations = WORKER_MAX_PBKDF2_ITERATIONS,
): Promise<string> {
  const clampedIterations = Math.min(Math.max(10_000, iterations), WORKER_MAX_PBKDF2_ITERATIONS);
  const combined = encoder.encode(`${clientMasterPasswordHash}:${pepper}`);
  const saltBytes = fromBase64(saltB64);

  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    combined,
    { name: "PBKDF2" },
    false,
    ["deriveBits"],
  );

  const derived = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: saltBytes,
      iterations: clampedIterations,
    },
    keyMaterial,
    256,
  );

  return toBase64(new Uint8Array(derived));
}

export async function verifyServerPassword(
  providedClientHash: string,
  storedHashB64: string,
  saltB64: string,
  pepper: string,
  iterations: number,
): Promise<boolean> {
  const computedB64 = await hashServerPassword(providedClientHash, saltB64, pepper, iterations);
  const [a, b] = await Promise.all([
    crypto.subtle.digest("SHA-256", encoder.encode(computedB64)),
    crypto.subtle.digest("SHA-256", encoder.encode(storedHashB64)),
  ]);
  return crypto.subtle.timingSafeEqual(a, b);
}
