/**
 * Zero-dependency HS256 JWT signer & verifier backed by Web Crypto API
 * with constant-time signature verification and Bitwarden-compatible claims.
 */

const encoder = new TextEncoder();
const decoder = new TextDecoder();

export interface AccessTokenClaims {
  sub: string;
  email: string;
  name: string;
  premium: boolean;
  sstamp: string;
  iss: string;
  iat: number;
  nbf: number;
  exp: number;
  amr: string[];
}

function base64UrlEncode(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]!);
  }
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlDecode(str: string): Uint8Array<ArrayBuffer> {
  let b64 = str.replace(/-/g, "+").replace(/_/g, "/");
  const pad = b64.length % 4;
  if (pad === 2) b64 += "==";
  else if (pad === 3) b64 += "=";
  else if (pad === 1) throw new Error("Invalid Base64URL");
  const bin = atob(b64);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out;
}

async function importHmacKey(secret: string, usages: ("sign" | "verify")[]): Promise<CryptoKey> {
  if (!secret || secret.length < 32) {
    throw new Error("JWT_SIGNING_SECRET must be at least 32 characters");
  }
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    usages,
  );
}

export async function signAccessToken(
  payload: Omit<AccessTokenClaims, "iat" | "nbf" | "exp" | "iss">,
  secret: string,
  ttlSeconds: number,
  issuer = "openvault",
): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const fullClaims: AccessTokenClaims = {
    ...payload,
    iss: issuer,
    iat: now,
    nbf: now,
    exp: now + ttlSeconds,
  };

  const headerPart = base64UrlEncode(encoder.encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const payloadPart = base64UrlEncode(encoder.encode(JSON.stringify(fullClaims)));
  const signingInput = `${headerPart}.${payloadPart}`;

  const key = await importHmacKey(secret, ["sign"]);
  const signature = new Uint8Array(
    await crypto.subtle.sign("HMAC", key, encoder.encode(signingInput)),
  );
  return `${signingInput}.${base64UrlEncode(signature)}`;
}

export async function verifyAccessToken(
  token: string,
  secret: string,
  expectedIssuer = "openvault",
): Promise<AccessTokenClaims | null> {
  const parts = token.split(".");
  if (parts.length !== 3) return null;

  const [headerPart, payloadPart, sigPart] = parts as [string, string, string];
  if (!headerPart || !payloadPart || !sigPart) return null;

  try {
    const header = JSON.parse(decoder.decode(base64UrlDecode(headerPart))) as {
      alg?: string;
      typ?: string;
    };
    if (header.alg !== "HS256") return null;

    const key = await importHmacKey(secret, ["sign"]);
    const expectedSig = await crypto.subtle.sign(
      "HMAC",
      key,
      encoder.encode(`${headerPart}.${payloadPart}`),
    );
    const providedSig = base64UrlDecode(sigPart);

    // Hash both to fixed 32 bytes before timingSafeEqual to avoid length side-channel
    const [a, b] = await Promise.all([
      crypto.subtle.digest("SHA-256", providedSig),
      crypto.subtle.digest("SHA-256", expectedSig),
    ]);
    if (!crypto.subtle.timingSafeEqual(a, b)) {
      return null;
    }

    const claims = JSON.parse(decoder.decode(base64UrlDecode(payloadPart))) as AccessTokenClaims;
    const now = Math.floor(Date.now() / 1000);
    if (
      typeof claims.sub !== "string" ||
      typeof claims.sstamp !== "string" ||
      claims.iss !== expectedIssuer ||
      typeof claims.exp !== "number" ||
      claims.exp <= now ||
      (typeof claims.nbf === "number" && claims.nbf > now + 30)
    ) {
      return null;
    }

    return claims;
  } catch {
    return null;
  }
}
