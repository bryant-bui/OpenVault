import { getBaseDomain } from "./domain-match.js";

const PUBLIC_SUFFIX_ONLY = new Set([
  "com",
  "org",
  "net",
  "io",
  "dev",
  "app",
  "gov",
  "edu",
  "vn",
  "uk",
  "jp",
  "au",
  "co.uk",
  "com.vn",
  "net.vn",
  "org.vn",
  "edu.vn",
  "gov.vn",
  "com.au",
  "co.jp",
  "com.sg",
]);

/**
 * Enforces WebAuthn Level 2/3 RP ID security rules:
 * - Origin MUST be HTTPS (or localhost / 127.0.0.1 for local development)
 * - rpId MUST equal the origin hostname OR be a valid registrable suffix of hostname
 * - Never allows bare TLDs/public suffixes as rpId
 */
export function isValidRpIdForOrigin(rpId: string, originUrl: string): boolean {
  const cleanRp = rpId.trim().toLowerCase().replace(/\.$/, "");
  if (!cleanRp || cleanRp.includes("/") || cleanRp.includes(":")) return false;

  let parsed: URL;
  try {
    parsed = new URL(originUrl);
  } catch {
    return false;
  }

  const host = parsed.hostname.toLowerCase().replace(/\.$/, "");
  const isLocal = host === "localhost" || host === "127.0.0.1";
  if (parsed.protocol !== "https:" && !isLocal) return false;

  if (cleanRp === host) return true;
  if (isLocal || /^\d{1,3}(\.\d{1,3}){3}$/.test(host)) return false;
  if (PUBLIC_SUFFIX_ONLY.has(cleanRp)) return false;

  // rpId must be at least the registrable eTLD+1 domain (not a TLD) and a dot-aligned suffix
  const baseDomain = getBaseDomain(host);
  if (cleanRp.length < baseDomain.length) return false;
  return host.endsWith(`.${cleanRp}`);
}

export const WEBAUTHN_BRIDGE_CHANNEL = "openvault-webauthn-bridge-v1";

export interface WebAuthnBridgeRequestEnvelope {
  channel: typeof WEBAUTHN_BRIDGE_CHANNEL;
  direction: "main-to-isolated";
  payload: SerializedWebAuthnRequest;
}

export interface WebAuthnBridgeResponseEnvelope {
  channel: typeof WEBAUTHN_BRIDGE_CHANNEL;
  direction: "isolated-to-main";
  payload: SerializedWebAuthnResponse;
}

export interface SerializedCreateRequest {
  kind: "create";
  requestId: string;
  origin: string;
  rpId: string;
  rpName: string;
  challengeB64Url: string;
  userIdB64Url: string;
  userName: string;
  userDisplayName: string;
  excludeCredentialIds: string[];
  discoverable: boolean;
}

export interface SerializedGetRequest {
  kind: "get";
  requestId: string;
  origin: string;
  rpId: string;
  challengeB64Url: string;
  allowCredentialIds: string[];
}

export type SerializedWebAuthnRequest = SerializedCreateRequest | SerializedGetRequest;

export interface SerializedCreateResponseData {
  credentialIdB64Url: string;
  clientDataJsonB64Url: string;
  attestationObjectB64Url: string;
  authenticatorDataB64Url: string;
  publicKeySpkiB64Url: string;
}

export interface SerializedGetResponseData {
  credentialIdB64Url: string;
  clientDataJsonB64Url: string;
  authenticatorDataB64Url: string;
  signatureDerB64Url: string;
  userHandleB64Url: string | null;
}

export type SerializedWebAuthnResponse =
  | { requestId: string; action: "fallback" }
  | { requestId: string; action: "cancel"; reason?: string }
  | { requestId: string; action: "ok"; kind: "create"; data: SerializedCreateResponseData }
  | { requestId: string; action: "ok"; kind: "get"; data: SerializedGetResponseData };

export interface PasskeyMatchSummary {
  cipherId: string;
  cipherName: string;
  credentialId: string;
  rpId: string;
  rpName: string;
  userName: string;
  userDisplayName: string;
}

export interface PasskeyTargetCipherSummary {
  id: string;
  name: string;
  username: string;
  hasPasskey: boolean;
}

export interface PasskeyPrepareCreateResponse {
  enabled: boolean;
  validOrigin: boolean;
  hasAccount: boolean;
  email: string | null;
  unlocked: boolean;
  pinAvailableNow: boolean;
  pinLength: number;
  alreadyExcluded: boolean;
  existingCiphers: PasskeyTargetCipherSummary[];
  recommendedCipherId: string | null;
}

export interface PasskeyPrepareGetResponse {
  enabled: boolean;
  validOrigin: boolean;
  hasAccount: boolean;
  email: string | null;
  unlocked: boolean;
  pinAvailableNow: boolean;
  pinLength: number;
  matches: PasskeyMatchSummary[];
}
