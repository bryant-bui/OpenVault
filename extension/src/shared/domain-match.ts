import type { CipherPayload } from "@openvault/crypto-core";
import type { AutofillCandidate } from "./types.js";

const MULTI_PART_TLDS = new Set([
  "co.uk",
  "org.uk",
  "ac.uk",
  "gov.uk",
  "com.au",
  "net.au",
  "org.au",
  "com.vn",
  "net.vn",
  "org.vn",
  "edu.vn",
  "gov.vn",
  "co.jp",
  "ne.jp",
  "or.jp",
  "com.sg",
  "com.br",
  "com.cn",
  "co.kr",
  "co.in",
  "com.tw",
  "com.hk",
]);

export function parseHostname(rawUrl: string | null | undefined): string | null {
  if (!rawUrl) return null;
  const trimmed = rawUrl.trim();
  if (!trimmed) return null;
  try {
    const withProto = /^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//.test(trimmed)
      ? trimmed
      : `https://${trimmed}`;
    const url = new URL(withProto);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    return url.hostname.toLowerCase();
  } catch {
    return null;
  }
}

export function getBaseDomain(hostname: string): string {
  const clean = hostname.toLowerCase().replace(/^www\./, "");
  // IPv4 / localhost
  if (clean === "localhost" || /^\d{1,3}(\.\d{1,3}){3}$/.test(clean) || clean.includes(":")) {
    return clean;
  }
  const parts = clean.split(".").filter(Boolean);
  if (parts.length <= 2) return clean;
  const lastTwo = parts.slice(-2).join(".");
  if (MULTI_PART_TLDS.has(lastTwo) && parts.length >= 3) {
    return parts.slice(-3).join(".");
  }
  return lastTwo;
}

export function scoreUriMatch(pageUrl: string, storedUri: string | null | undefined): number {
  const pageHost = parseHostname(pageUrl);
  const storedHost = parseHostname(storedUri);
  if (!pageHost || !storedHost) return 0;

  if (pageHost === storedHost) return 100;
  if (pageHost.replace(/^www\./, "") === storedHost.replace(/^www\./, "")) return 95;

  const pageBase = getBaseDomain(pageHost);
  const storedBase = getBaseDomain(storedHost);
  if (pageBase === storedBase) return 75;

  return 0;
}

export function findMatchingLogins(
  ciphers: CipherPayload[],
  pageUrl: string,
): AutofillCandidate[] {
  const candidates: AutofillCandidate[] = [];

  for (const cipher of ciphers) {
    if (cipher.deletedDate || cipher.type !== 1 || !cipher.login || !cipher.id) continue;

    let bestScore = 0;
    let bestUri: string | null = null;

    for (const u of cipher.login.uris ?? []) {
      const score = scoreUriMatch(pageUrl, u.uri);
      if (score > bestScore) {
        bestScore = score;
        bestUri = u.uri ?? null;
      }
    }

    if (bestScore > 0) {
      candidates.push({
        id: cipher.id,
        name: cipher.name,
        username: cipher.login.username ?? "",
        password: cipher.login.password ?? "",
        totp: cipher.login.totp ?? null,
        uri: bestUri,
        score: bestScore + (cipher.favorite ? 5 : 0),
      });
    }
  }

  return candidates.sort((a, b) => b.score - a.score || a.name.localeCompare(b.name));
}

export function extractDisplayDomain(rawUrl: string | null | undefined): string {
  const host = parseHostname(rawUrl);
  return host ? host.replace(/^www\./, "") : "";
}

function getCandidateIdentifiers(cipher: CipherPayload): string[] {
  const ids: string[] = [];
  if (cipher.login?.username?.trim()) ids.push(cipher.login.username.trim().toLowerCase());
  for (const field of cipher.fields ?? []) {
    const fname = (field.name ?? "").trim().toLowerCase();
    const val = (field.value ?? "").trim().toLowerCase();
    if (val && (fname === "email" || fname === "e-mail" || fname === "username" || fname === "user")) {
      ids.push(val);
    }
  }
  for (const pk of cipher.login?.fido2Credentials ?? []) {
    if (pk.userName?.trim()) ids.push(pk.userName.trim().toLowerCase());
  }
  return Array.from(new Set(ids));
}

function getEmailLocalPart(value: string): string {
  const clean = value.trim().toLowerCase();
  const at = clean.indexOf("@");
  return at > 0 ? clean.slice(0, at) : clean;
}

/**
 * Matches an existing active Login cipher for password updates so saving a changed
 * password updates the existing account instead of creating a duplicate entry.
 */
export function findMatchingLoginCipher(
  ciphers: CipherPayload[],
  pageUrl: string,
  username: string,
  preferredCipherId?: string | null,
): CipherPayload | null {
  const activeLogins = ciphers.filter((c) => !c.deletedDate && c.type === 1 && c.login && c.id);
  if (preferredCipherId) {
    const preferred = activeLogins.find((c) => c.id === preferredCipherId);
    if (preferred) return preferred;
  }

  const domainMatches = findMatchingLogins(activeLogins, pageUrl);
  if (domainMatches.length === 0) return null;

  const domainCiphers = domainMatches
    .map((m) => activeLogins.find((c) => c.id === m.id))
    .filter((c): c is CipherPayload => Boolean(c));

  const cleanUser = username.trim().toLowerCase();
  if (!cleanUser) {
    return domainCiphers.length === 1 ? domainCiphers[0]! : null;
  }

  // Tier 1: Exact username/email match (including Email custom field)
  const exact = domainCiphers.find((c) => getCandidateIdentifiers(c).includes(cleanUser));
  if (exact) return exact;

  // Tier 2: Email local-part match ("octocat" <-> "octocat@example.com")
  const inputLocal = getEmailLocalPart(cleanUser);
  if (inputLocal) {
    const localMatch = domainCiphers.find((c) =>
      getCandidateIdentifiers(c).some((id) => getEmailLocalPart(id) === inputLocal),
    );
    if (localMatch) return localMatch;
  }

  // Tier 3: Single login stored for this domain (and stored username is blank or closely named)
  if (domainCiphers.length === 1 && !domainCiphers[0]!.login?.username?.trim()) {
    return domainCiphers[0]!;
  }

  return null;
}

export interface PasskeyTargetMatchResult {
  candidates: Array<{ id: string; name: string; username: string; hasPasskey: boolean }>;
  recommendedId: string | null;
}

/**
 * Finds existing active Login ciphers eligible to receive a newly created Passkey,
 * scoring exact user match -> local-part/displayName match -> single-login-on-site fallback.
 */
export function findPasskeyTargetLoginCiphers(
  ciphers: CipherPayload[],
  origin: string,
  rpId: string,
  userName?: string | null,
  userDisplayName?: string | null,
): PasskeyTargetMatchResult {
  const cleanRp = rpId.trim().toLowerCase().replace(/\.$/, "");
  const cleanRpBase = cleanRp ? getBaseDomain(cleanRp) : "";
  const activeLogins = ciphers.filter((c) => !c.deletedDate && c.type === 1 && c.login && c.id);

  const scored: Array<{ cipher: CipherPayload; domainScore: number }> = [];
  for (const c of activeLogins) {
    let bestScore = 0;
    for (const u of c.login?.uris ?? []) {
      const originScore = scoreUriMatch(origin, u.uri);
      if (originScore > bestScore) bestScore = originScore;
      const storedHost = parseHostname(u.uri);
      if (storedHost && cleanRp) {
        if (storedHost === cleanRp) {
          bestScore = Math.max(bestScore, 100);
        } else if (storedHost.endsWith(`.${cleanRp}`) || getBaseDomain(storedHost) === cleanRpBase) {
          bestScore = Math.max(bestScore, 75);
        }
      }
    }
    const hasRpPasskey = Boolean(
      cleanRp &&
        c.login?.fido2Credentials?.some(
          (f) => f.rpId.trim().toLowerCase().replace(/\.$/, "") === cleanRp,
        ),
    );
    if (hasRpPasskey) bestScore = Math.max(bestScore, 95);
    if (bestScore > 0) {
      scored.push({ cipher: c, domainScore: bestScore + (c.favorite ? 5 : 0) });
    }
  }

  scored.sort((a, b) => b.domainScore - a.domainScore || a.cipher.name.localeCompare(b.cipher.name));

  const cleanUser = (userName ?? "").trim().toLowerCase();
  const cleanDisplay = (userDisplayName ?? "").trim().toLowerCase();
  const userLocal = cleanUser ? getEmailLocalPart(cleanUser) : "";

  let recommendedId: string | null = null;
  if (cleanUser) {
    // Tier 1: Exact username / Email custom field / existing passkey username match
    recommendedId =
      scored.find(({ cipher }) => getCandidateIdentifiers(cipher).includes(cleanUser))?.cipher.id ??
      null;
  }
  if (!recommendedId && (userLocal || cleanDisplay)) {
    // Tier 2: Email local-part or display name match
    recommendedId =
      scored.find(({ cipher }) =>
        getCandidateIdentifiers(cipher).some((id) => {
          const idLocal = getEmailLocalPart(id);
          return (
            (userLocal && idLocal === userLocal) ||
            (cleanDisplay && (id === cleanDisplay || idLocal === getEmailLocalPart(cleanDisplay)))
          );
        }),
      )?.cipher.id ?? null;
  }
  if (!recommendedId && scored.length === 1) {
    // Tier 3: Only one login item exists for this site -> attach to it instead of creating a duplicate
    recommendedId = scored[0]!.cipher.id!;
  }

  return {
    candidates: scored.map(({ cipher }) => ({
      id: cipher.id!,
      name: cipher.name,
      username: cipher.login?.username ?? "",
      hasPasskey: (cipher.login?.fido2Credentials?.length ?? 0) > 0,
    })),
    recommendedId,
  };
}
