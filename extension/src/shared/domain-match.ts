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
