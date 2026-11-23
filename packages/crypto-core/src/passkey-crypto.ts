import {
  base64UrlToBytes,
  bytesToBase64Url,
  concatBytes,
  randomBytes,
  toArrayBufferView,
  utf8ToBytes,
} from "./bytes.js";
import type { Fido2Credential } from "./types.js";

/**
 * Minimal deterministic CBOR encoder (RFC 8949) supporting the types required
 * by WebAuthn COSE keys & attestationObject (integers, bytes, strings, maps).
 */
export type CborValue =
  | number
  | string
  | Uint8Array
  | Map<number | string, CborValue>
  | { [key: string]: CborValue };

function encodeCborHead(majorType: number, value: number): Uint8Array<ArrayBuffer> {
  const mt = majorType << 5;
  if (value < 24) {
    return new Uint8Array([mt | value]);
  }
  if (value <= 0xff) {
    return new Uint8Array([mt | 24, value]);
  }
  if (value <= 0xffff) {
    const out = new Uint8Array(3);
    out[0] = mt | 25;
    out[1] = (value >>> 8) & 0xff;
    out[2] = value & 0xff;
    return out;
  }
  const out = new Uint8Array(5);
  out[0] = mt | 26;
  out[1] = (value >>> 24) & 0xff;
  out[2] = (value >>> 16) & 0xff;
  out[3] = (value >>> 8) & 0xff;
  out[4] = value & 0xff;
  return out;
}

export function encodeCbor(value: CborValue): Uint8Array<ArrayBuffer> {
  if (typeof value === "number") {
    if (!Number.isInteger(value)) {
      throw new Error("Only integer CBOR values are supported");
    }
    if (value >= 0) {
      return encodeCborHead(0, value);
    }
    return encodeCborHead(1, -1 - value);
  }

  if (typeof value === "string") {
    const bytes = utf8ToBytes(value);
    return concatBytes(encodeCborHead(3, bytes.byteLength), bytes);
  }

  if (value instanceof Uint8Array) {
    return concatBytes(encodeCborHead(2, value.byteLength), toArrayBufferView(value));
  }

  const entries: [number | string, CborValue][] =
    value instanceof Map ? Array.from(value.entries()) : Object.entries(value);

  const chunks: Uint8Array[] = [encodeCborHead(5, entries.length)];
  for (const [k, v] of entries) {
    chunks.push(encodeCbor(k));
    chunks.push(encodeCbor(v));
  }
  return concatBytes(...chunks);
}

/**
 * Converts a 64-byte IEEE P1363 ECDSA P-256 signature (r || s from Web Crypto)
 * into ASN.1 DER encoding required by WebAuthn/FIDO2 relying parties.
 */
export function p1363ToDer(signature: Uint8Array): Uint8Array<ArrayBuffer> {
  if (signature.byteLength !== 64) {
    throw new Error(`Expected 64-byte P-256 P1363 signature, got ${signature.byteLength}`);
  }

  const encodeInt = (raw: Uint8Array): Uint8Array<ArrayBuffer> => {
    let start = 0;
    while (start < raw.length - 1 && raw[start] === 0) {
      start++;
    }
    let slice = raw.subarray(start);
    if ((slice[0] ?? 0) & 0x80) {
      slice = concatBytes(new Uint8Array([0x00]), slice);
    }
    return concatBytes(new Uint8Array([0x02, slice.byteLength]), slice);
  };

  const rDer = encodeInt(signature.subarray(0, 32));
  const sDer = encodeInt(signature.subarray(32, 64));
  const seqLen = rDer.byteLength + sDer.byteLength;
  return concatBytes(new Uint8Array([0x30, seqLen]), rDer, sDer);
}

/**
 * Encodes an uncompressed P-256 public key (x, y coordinates) as COSE EC2 Key (ES256, alg=-7, crv=1).
 */
export function encodeCoseP256PublicKey(
  x: Uint8Array,
  y: Uint8Array,
): Uint8Array<ArrayBuffer> {
  if (x.byteLength !== 32 || y.byteLength !== 32) {
    throw new Error("P-256 coordinates must be 32 bytes each");
  }
  const coseMap = new Map<number, CborValue>([
    [1, 2],  // kty: EC2
    [3, -7], // alg: ES256
    [-1, 1], // crv: P-256
    [-2, toArrayBufferView(x)],
    [-3, toArrayBufferView(y)],
  ]);
  return encodeCbor(coseMap);
}

// OpenVault AAGUID (16 bytes zero for privacy-preserving software passkey authenticator)
const OPENVAULT_AAGUID = new Uint8Array(16);

export interface CreatePasskeyParams {
  rpId: string;
  rpName?: string;
  userHandle: Uint8Array;
  userName: string;
  userDisplayName?: string;
  discoverable?: boolean;
}

export interface CreatedPasskeyResult {
  credential: Fido2Credential;
  credentialIdBytes: Uint8Array<ArrayBuffer>;
  authenticatorData: Uint8Array<ArrayBuffer>;
  attestationObject: Uint8Array<ArrayBuffer>;
  publicKeySpki: Uint8Array<ArrayBuffer>;
}

/**
 * Generates an ECDSA P-256 Passkey compatible with Bitwarden `fido2Credentials`
 * and returns WebAuthn attestation payloads ready for `navigator.credentials.create`.
 */
export async function createPasskeyCredential(
  params: CreatePasskeyParams,
): Promise<CreatedPasskeyResult> {
  const generated = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"],
  );
  if (!("publicKey" in generated)) {
    throw new Error("Expected ECDSA CryptoKeyPair");
  }

  const [pkcs8Buf, spkiBuf, jwkPub] = await Promise.all([
    crypto.subtle.exportKey("pkcs8", generated.privateKey),
    crypto.subtle.exportKey("spki", generated.publicKey),
    crypto.subtle.exportKey("jwk", generated.publicKey),
  ]);

  if (!(pkcs8Buf instanceof ArrayBuffer) || !(spkiBuf instanceof ArrayBuffer) || jwkPub instanceof ArrayBuffer) {
    throw new Error("Unexpected ECDSA key export format");
  }
  if (!jwkPub.x || !jwkPub.y) {
    throw new Error("Failed to export P-256 public coordinates");
  }

  const x = base64UrlToBytes(jwkPub.x);
  const y = base64UrlToBytes(jwkPub.y);
  const cosePublicKey = encodeCoseP256PublicKey(x, y);

  const credentialIdBytes = randomBytes(16);
  const rpIdHash = new Uint8Array(await crypto.subtle.digest("SHA-256", utf8ToBytes(params.rpId)));

  // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) | AT (0x40) = 0x5d
  const flags = new Uint8Array([0x5d]);
  const signCount = new Uint8Array([0, 0, 0, 0]);
  const credIdLen = new Uint8Array([
    (credentialIdBytes.byteLength >>> 8) & 0xff,
    credentialIdBytes.byteLength & 0xff,
  ]);

  const authenticatorData = concatBytes(
    rpIdHash,
    flags,
    signCount,
    OPENVAULT_AAGUID,
    credIdLen,
    credentialIdBytes,
    cosePublicKey,
  );

  const attestationObject = encodeCbor({
    fmt: "none",
    attStmt: {},
    authData: authenticatorData,
  });

  const credential: Fido2Credential = {
    credentialId: bytesToBase64Url(credentialIdBytes),
    keyType: "public-key",
    keyAlgorithm: "ECDSA",
    keyCurve: "P-256",
    keyValue: bytesToBase64Url(new Uint8Array(pkcs8Buf)),
    rpId: params.rpId,
    rpName: params.rpName ?? params.rpId,
    userHandle: bytesToBase64Url(params.userHandle),
    userName: params.userName,
    userDisplayName: params.userDisplayName ?? params.userName,
    counter: "0",
    discoverable: String(params.discoverable ?? true),
    creationDate: new Date().toISOString(),
  };

  return {
    credential,
    credentialIdBytes,
    authenticatorData,
    attestationObject,
    publicKeySpki: new Uint8Array(spkiBuf),
  };
}

export interface SignedAssertionResult {
  authenticatorData: Uint8Array<ArrayBuffer>;
  signatureDer: Uint8Array<ArrayBuffer>;
  nextCounter: number;
}

/**
 * Signs a WebAuthn assertion challenge using a stored Bitwarden `Fido2Credential`.
 */
export async function signPasskeyAssertion(
  credential: Fido2Credential,
  clientDataJson: Uint8Array,
): Promise<SignedAssertionResult> {
  const pkcs8Bytes = base64UrlToBytes(credential.keyValue);
  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    pkcs8Bytes,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );

  const rpIdHash = new Uint8Array(await crypto.subtle.digest("SHA-256", utf8ToBytes(credential.rpId)));
  const currentCounter = Number.parseInt(credential.counter || "0", 10) || 0;
  const nextCounter = currentCounter > 0 ? currentCounter + 1 : 0;

  // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) = 0x1d
  const flags = new Uint8Array([0x1d]);
  const signCountBuf = new ArrayBuffer(4);
  new DataView(signCountBuf).setUint32(0, nextCounter, false);

  const authenticatorData = concatBytes(rpIdHash, flags, new Uint8Array(signCountBuf));
  const clientDataHash = new Uint8Array(
    await crypto.subtle.digest("SHA-256", toArrayBufferView(clientDataJson)),
  );

  const signedPayload = concatBytes(authenticatorData, clientDataHash);
  const sigP1363 = new Uint8Array(
    await crypto.subtle.sign(
      { name: "ECDSA", hash: "SHA-256" },
      privateKey,
      signedPayload,
    ),
  );

  return {
    authenticatorData,
    signatureDer: p1363ToDer(sigP1363),
    nextCounter,
  };
}
