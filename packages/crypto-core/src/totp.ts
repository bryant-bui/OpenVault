import { base32ToBytes } from "./bytes.js";

export type TotpAlgorithm = "SHA-1" | "SHA-256" | "SHA-512";

export interface TotpParams {
  secret: string;
  algorithm: TotpAlgorithm;
  digits: number;
  period: number;
  issuer?: string;
  label?: string;
}

export interface TotpCodeResult {
  code: string;
  period: number;
  remainingSeconds: number;
  expiresAtMs: number;
}

function normalizeAlgorithm(alg?: string | null): TotpAlgorithm {
  const upper = (alg ?? "SHA1").toUpperCase().replace(/[^A-Z0-9]/g, "");
  switch (upper) {
    case "SHA1":
      return "SHA-1";
    case "SHA256":
      return "SHA-256";
    case "SHA512":
      return "SHA-512";
    default:
      throw new Error(`Unsupported TOTP algorithm: ${alg}`);
  }
}

/**
 * Parses either a full `otpauth://totp/...` URI or a raw Base32 secret string.
 */
export function parseTotpInput(input: string): TotpParams {
  const trimmed = input.trim();
  if (!trimmed) {
    throw new Error("TOTP secret or URI is empty");
  }

  if (trimmed.toLowerCase().startsWith("otpauth://")) {
    const url = new URL(trimmed);
    if (url.hostname.toLowerCase() !== "totp") {
      throw new Error(`Unsupported OTP type: ${url.hostname}`);
    }
    const secret = url.searchParams.get("secret");
    if (!secret) {
      throw new Error("otpauth URI missing required 'secret' parameter");
    }
    const algorithm = normalizeAlgorithm(url.searchParams.get("algorithm"));
    const digitsRaw = url.searchParams.get("digits");
    const periodRaw = url.searchParams.get("period");
    const digits = digitsRaw ? parseInt(digitsRaw, 10) : 6;
    const period = periodRaw ? parseInt(periodRaw, 10) : 30;

    if (!Number.isInteger(digits) || digits < 6 || digits > 10) {
      throw new Error(`Invalid TOTP digits: ${digits}`);
    }
    if (!Number.isInteger(period) || period < 1 || period > 300) {
      throw new Error(`Invalid TOTP period: ${period}`);
    }

    const rawLabel = decodeURIComponent(url.pathname.replace(/^\/+/, ""));
    const issuerParam = url.searchParams.get("issuer") ?? undefined;
    const issuer = issuerParam || (rawLabel.includes(":") ? rawLabel.split(":")[0] : undefined);

    return {
      secret: secret.trim(),
      algorithm,
      digits,
      period,
      issuer,
      label: rawLabel || undefined,
    };
  }

  return {
    secret: trimmed,
    algorithm: "SHA-1",
    digits: 6,
    period: 30,
  };
}

/**
 * Generates an RFC 6238 TOTP code using Web Crypto HMAC.
 */
export async function generateTotp(
  secretOrUri: string,
  timestampMs: number = Date.now(),
): Promise<TotpCodeResult> {
  const params = parseTotpInput(secretOrUri);
  const secretBytes = base32ToBytes(params.secret);

  const epochSeconds = Math.floor(timestampMs / 1000);
  const counter = BigInt(Math.floor(epochSeconds / params.period));

  const counterBuf = new ArrayBuffer(8);
  const counterView = new DataView(counterBuf);
  counterView.setBigUint64(0, counter, false);

  const hmacKey = await crypto.subtle.importKey(
    "raw",
    secretBytes,
    { name: "HMAC", hash: params.algorithm },
    false,
    ["sign"],
  );

  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", hmacKey, counterBuf));
  const offset = (digest[digest.length - 1] ?? 0) & 0x0f;

  const binary =
    (((digest[offset] ?? 0) & 0x7f) << 24) |
    (((digest[offset + 1] ?? 0) & 0xff) << 16) |
    (((digest[offset + 2] ?? 0) & 0xff) << 8) |
    ((digest[offset + 3] ?? 0) & 0xff);

  const mod = 10 ** params.digits;
  const code = String(binary % mod).padStart(params.digits, "0");

  const elapsedInPeriod = epochSeconds % params.period;
  const remainingSeconds = params.period - elapsedInPeriod;
  const expiresAtMs = (epochSeconds + remainingSeconds) * 1000;

  return {
    code,
    period: params.period,
    remainingSeconds,
    expiresAtMs,
  };
}
