const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder("utf-8", { fatal: true, ignoreBOM: false });

export function utf8ToBytes(str: string): Uint8Array<ArrayBuffer> {
  const encoded = textEncoder.encode(str);
  const copy = new Uint8Array(new ArrayBuffer(encoded.byteLength));
  copy.set(encoded);
  return copy;
}

export function bytesToUtf8(bytes: Uint8Array): string {
  return textDecoder.decode(bytes);
}

export function toArrayBufferView(bytes: Uint8Array): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(new ArrayBuffer(bytes.byteLength));
  out.set(bytes);
  return out;
}

export function concatBytes(...arrays: Uint8Array[]): Uint8Array<ArrayBuffer> {
  let total = 0;
  for (const arr of arrays) total += arr.byteLength;
  const result = new Uint8Array(new ArrayBuffer(total));
  let offset = 0;
  for (const arr of arrays) {
    result.set(arr, offset);
    offset += arr.byteLength;
  }
  return result;
}

export function randomBytes(length: number): Uint8Array<ArrayBuffer> {
  const buf = new Uint8Array(new ArrayBuffer(length));
  crypto.getRandomValues(buf);
  return buf;
}

export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    const chunk = bytes.subarray(i, i + chunkSize);
    binary += String.fromCharCode(...chunk);
  }
  return btoa(binary);
}

export function base64ToBytes(b64: string): Uint8Array<ArrayBuffer> {
  if (typeof b64 !== "string" || b64.length === 0) {
    throw new Error("Invalid Base64 string: empty");
  }
  const normalized = b64.replace(/\s+/g, "");
  const binary = atob(normalized);
  const bytes = new Uint8Array(new ArrayBuffer(binary.length));
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function bytesToBase64Url(bytes: Uint8Array): string {
  return bytesToBase64(bytes).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function base64UrlToBytes(b64url: string): Uint8Array<ArrayBuffer> {
  let b64 = b64url.replace(/-/g, "+").replace(/_/g, "/");
  const pad = b64.length % 4;
  if (pad === 2) b64 += "==";
  else if (pad === 3) b64 += "=";
  else if (pad === 1) throw new Error("Invalid Base64URL string length");
  return base64ToBytes(b64);
}

export function bytesToHex(bytes: Uint8Array): string {
  let hex = "";
  for (let i = 0; i < bytes.length; i++) {
    hex += (bytes[i] ?? 0).toString(16).padStart(2, "0");
  }
  return hex;
}

export function hexToBytes(hex: string): Uint8Array<ArrayBuffer> {
  const clean = hex.trim();
  if (clean.length % 2 !== 0 || !/^[0-9a-fA-F]*$/.test(clean)) {
    throw new Error("Invalid hex string");
  }
  const out = new Uint8Array(new ArrayBuffer(clean.length / 2));
  for (let i = 0; i < out.length; i++) {
    out[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16);
  }
  return out;
}

const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export function base32ToBytes(input: string): Uint8Array<ArrayBuffer> {
  const cleaned = input.toUpperCase().replace(/[\s\-=]+/g, "");
  if (cleaned.length === 0) {
    throw new Error("Empty Base32 secret");
  }

  let bits = 0;
  let value = 0;
  const output: number[] = [];

  for (let i = 0; i < cleaned.length; i++) {
    const ch = cleaned[i]!;
    const idx = BASE32_ALPHABET.indexOf(ch);
    if (idx === -1) {
      throw new Error(`Invalid Base32 character: ${ch}`);
    }
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      bits -= 8;
      output.push((value >>> bits) & 0xff);
    }
  }

  const result = new Uint8Array(new ArrayBuffer(output.length));
  result.set(output);
  return result;
}

/**
 * Constant-time equality check for equal-length byte buffers.
 * Never short-circuits on value mismatch.
 */
export function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
  if (a.byteLength !== b.byteLength) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.byteLength; i++) {
    diff |= (a[i] ?? 0) ^ (b[i] ?? 0);
  }
  return diff === 0;
}

/**
 * Best-effort zeroization of sensitive key material in memory.
 */
export function zeroize(...buffers: (Uint8Array | null | undefined)[]): void {
  for (const buf of buffers) {
    if (buf) {
      buf.fill(0);
    }
  }
}
