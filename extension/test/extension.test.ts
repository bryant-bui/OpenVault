import fs from "node:fs";
import path from "node:path";
import { CipherType, type CipherPayload } from "@openvault/crypto-core";
import { describe, expect, it } from "vitest";
import {
  extractDisplayDomain,
  findMatchingLoginCipher,
  findMatchingLogins,
  findPasskeyTargetLoginCiphers,
  getBaseDomain,
  parseHostname,
  scoreUriMatch,
} from "../src/shared/domain-match.js";
import { classifyField, formatTotpCode, maskCardNumber } from "../src/shared/field-classifier.js";
import { isValidRpIdForOrigin } from "../src/shared/passkey-bridge.js";
import {
  RECOVERY_CODES_DEFAULT_SLOTS,
  applyRecoveryCodesPaste,
  parseRecoveryCodesPaste,
  parseRecoveryCodesToSlots,
  serializeRecoveryCodesSlots,
} from "../src/shared/recovery-codes.js";

const EXT_ROOT = path.resolve(import.meta.dirname, "..");

describe("OpenVault Extension — Domain & URI Matching", () => {
  it("extracts base domains accurately including multi-part TLDs and localhost", () => {
    expect(getBaseDomain("accounts.google.com")).toBe("google.com");
    expect(getBaseDomain("www.github.com")).toBe("github.com");
    expect(getBaseDomain("ebanking.vietcombank.com.vn")).toBe("vietcombank.com.vn");
    expect(getBaseDomain("portal.bbc.co.uk")).toBe("bbc.co.uk");
    expect(getBaseDomain("localhost")).toBe("localhost");
    expect(getBaseDomain("127.0.0.1")).toBe("127.0.0.1");
    expect(parseHostname("chrome://extensions")).toBeNull();
    expect(extractDisplayDomain("https://www.cloudflare.com/login")).toBe("cloudflare.com");
  });

  it("scores exact host > www match > subdomain match and ranks favorites higher", () => {
    expect(scoreUriMatch("https://github.com/login", "https://github.com")).toBe(100);
    expect(scoreUriMatch("https://www.github.com/login", "https://github.com")).toBe(95);
    expect(scoreUriMatch("https://gist.github.com", "https://github.com")).toBe(75);
    expect(scoreUriMatch("https://evilgithub.com", "https://github.com")).toBe(0);

    const ciphers: CipherPayload[] = [
      {
        id: "1",
        type: CipherType.Login,
        name: "Subdomain Account",
        favorite: false,
        login: {
          username: "sub@test.io",
          password: "pw1",
          uris: [{ uri: "https://gist.github.com" }],
        },
      },
      {
        id: "2",
        type: CipherType.Login,
        name: "Main GitHub",
        favorite: true,
        login: {
          username: "main@test.io",
          password: "pw2",
          totp: "JBSWY3DPEHPK3PXP",
          uris: [{ uri: "https://github.com/login" }],
        },
      },
      {
        id: "3",
        type: CipherType.Login,
        name: "Deleted GitHub",
        deletedDate: "2026-11-20T00:00:00.000Z",
        login: {
          username: "old@test.io",
          password: "pw3",
          uris: [{ uri: "https://github.com" }],
        },
      },
    ];

    const matches = findMatchingLogins(ciphers, "https://github.com/session");
    expect(matches).toHaveLength(2);
    expect(matches[0]!.id).toBe("2");
    expect(matches[0]!.totp).toBe("JBSWY3DPEHPK3PXP");
    expect(matches[1]!.id).toBe("1");
  });
});

describe("OpenVault Extension — Manifest V3 & Asset Integrity", () => {
  it("declares valid Manifest V3 with real PNG icons (including locked icons) and no Side Panel", () => {
    const manifestPath = path.join(EXT_ROOT, "public/manifest.json");
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8")) as {
      manifest_version: number;
      version: string;
      icons: Record<string, string>;
      action: { default_popup?: string; default_icon?: Record<string, string> };
      side_panel?: unknown;
      background: { service_worker: string };
      permissions: string[];
      content_scripts: Array<{
        js: string[];
        run_at?: string;
        world?: string;
        all_frames?: boolean;
      }>;
      content_security_policy: { extension_pages: string };
    };

    expect(manifest.manifest_version).toBe(3);
    expect(manifest.version).toBe("0.6.0");
    expect(manifest.background.service_worker).toBe("background.js");
    expect(manifest.action.default_popup).toBe("popup.html");
    expect(manifest.side_panel).toBeUndefined();
    expect(manifest.permissions).not.toContain("sidePanel");
    expect(manifest.permissions).toContain("tabs");
    expect(manifest.permissions).toContain("offscreen");
    expect(manifest.permissions).toContain("privacy");
    expect(manifest.permissions).toContain("webRequest");
    expect(manifest.permissions).toContain("webRequestAuthProvider");
    expect(manifest.content_security_policy.extension_pages).toContain("wasm-unsafe-eval");
    expect(fs.existsSync(path.join(EXT_ROOT, "sidepanel.html"))).toBe(false);

    // Verify MAIN-world WebAuthn Passkey interceptor + ISOLATED document_start bridge
    const mainScript = manifest.content_scripts.find(
      (cs) => cs.world === "MAIN" && cs.js.includes("webauthn-main.js"),
    );
    expect(mainScript).toBeDefined();
    expect(mainScript?.run_at).toBe("document_start");
    expect(mainScript?.all_frames).toBe(false);

    const isolatedScript = manifest.content_scripts.find((cs) =>
      cs.js.includes("content-autofill.js"),
    );
    expect(isolatedScript).toBeDefined();
    expect(isolatedScript?.run_at).toBe("document_start");

    // Verify offscreen document exists with zero inline scripts
    const offscreenHtml = fs.readFileSync(path.join(EXT_ROOT, "public/offscreen.html"), "utf8");
    expect(offscreenHtml).toContain('src="offscreen.js"');
    expect(offscreenHtml).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/i);
    expect(fs.existsSync(path.join(EXT_ROOT, "public/offscreen.js"))).toBe(true);

    // Verify every unlocked & locked icon exists and has valid PNG header + exact declared dimensions
    const allIconSets = [manifest.icons, manifest.action.default_icon ?? {}];
    for (const iconSet of allIconSets) {
      for (const [sizeStr, relPath] of Object.entries(iconSet)) {
        const expected = Number(sizeStr);
        const absPath = path.join(EXT_ROOT, "public", relPath);
        expect(fs.existsSync(absPath)).toBe(true);
        const buf = fs.readFileSync(absPath);
        expect(buf.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");
        expect(buf.readUInt32BE(16)).toBe(expected);
        expect(buf.readUInt32BE(20)).toBe(expected);
      }
    }

    // Verify popup.html has zero inline scripts or inline event handlers
    const html = fs.readFileSync(path.join(EXT_ROOT, "popup.html"), "utf8");
    expect(html).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/i);
    expect(html).not.toMatch(/\son(click|load|submit|change)=/i);
  });
});

describe("OpenVault Extension — Smart Field Classifier (Login, 2FA, Card, Identity, New Password)", () => {
  const base = {
    type: "text",
    name: "",
    id: "",
    autocomplete: "",
    placeholder: "",
    ariaLabel: "",
  };

  it("distinguishes login password vs registration new-password accurately", () => {
    expect(classifyField({ ...base, type: "password", autocomplete: "current-password" })).toBe(
      "login",
    );
    expect(classifyField({ ...base, type: "password", autocomplete: "new-password" })).toBe(
      "new-password",
    );
    expect(classifyField({ ...base, type: "password", formPasswordCount: 2 })).toBe("new-password");
    expect(
      classifyField({ ...base, type: "password", placeholder: "Nhập lại mật khẩu" }),
    ).toBe("new-password");
  });

  it("detects single and split 6-box 2FA OTP fields while ignoring coupons/captchas/search", () => {
    expect(classifyField({ ...base, autocomplete: "one-time-code" })).toBe("totp");
    expect(classifyField({ ...base, name: "totp_code", placeholder: "Mã 2FA 6 chữ số" })).toBe(
      "totp",
    );
    expect(classifyField({ ...base, maxLength: 1, siblingSingleCharInputCount: 6 })).toBe("totp");
    expect(classifyField({ ...base, name: "promo_code", placeholder: "Coupon code" })).toBeNull();
    expect(classifyField({ ...base, type: "search", placeholder: "Search..." })).toBeNull();
    expect(formatTotpCode("123456")).toBe("123 456");
  });

  it("detects credit card and identity fields cleanly", () => {
    expect(classifyField({ ...base, autocomplete: "cc-number" })).toBe("card");
    expect(classifyField({ ...base, name: "card_cvv", placeholder: "CVV" })).toBe("card");
    expect(maskCardNumber("4242 4242 4242 1234")).toBe("•••• 1234");

    expect(
      classifyField({ ...base, autocomplete: "street-address", formHasPasswordField: false }),
    ).toBe("identity");
    expect(
      classifyField({ ...base, placeholder: "Số điện thoại", formHasPasswordField: false }),
    ).toBe("identity");
    // Inside a login form with a password field, non-username text shouldn't hijack as identity
    expect(
      classifyField({ ...base, autocomplete: "street-address", formHasPasswordField: true }),
    ).toBeNull();
  });

  it("detects Adobe-style autocomplete='username webauthn' and label-only email inputs", () => {
    expect(classifyField({ ...base, autocomplete: "username webauthn" })).toBe("login");
    expect(classifyField({ ...base, labelText: "Email address", type: "text" })).toBe("login");
  });
});

describe("OpenVault Extension — WebAuthn Passkey RP ID Security Validation", () => {
  it("allows exact host, valid registrable parent domain on HTTPS, and localhost", () => {
    expect(isValidRpIdForOrigin("github.com", "https://github.com")).toBe(true);
    expect(isValidRpIdForOrigin("github.com", "https://gist.github.com")).toBe(true);
    expect(isValidRpIdForOrigin("webauthn.io", "https://webauthn.io")).toBe(true);
    expect(isValidRpIdForOrigin("vietcombank.com.vn", "https://ebanking.vietcombank.com.vn")).toBe(
      true,
    );
    expect(isValidRpIdForOrigin("localhost", "http://localhost:3000")).toBe(true);
    expect(isValidRpIdForOrigin("127.0.0.1", "http://127.0.0.1:8787")).toBe(true);
  });

  it("rejects HTTP non-local origins, public suffixes, and cross-domain RP ID spoofing", () => {
    expect(isValidRpIdForOrigin("github.com", "http://github.com")).toBe(false);
    expect(isValidRpIdForOrigin("com", "https://github.com")).toBe(false);
    expect(isValidRpIdForOrigin("com.vn", "https://ebanking.vietcombank.com.vn")).toBe(false);
    expect(isValidRpIdForOrigin("co.uk", "https://portal.bbc.co.uk")).toBe(false);
    expect(isValidRpIdForOrigin("github.com", "https://evilgithub.com")).toBe(false);
    expect(isValidRpIdForOrigin("google.com", "https://github.com")).toBe(false);
    expect(isValidRpIdForOrigin("sub.github.com", "https://github.com")).toBe(false);
    expect(isValidRpIdForOrigin("", "https://github.com")).toBe(false);
  });
});

describe("OpenVault Extension — Recovery Codes Flexible Pairs, Legacy Migration & >10 Slots", () => {
  it("preserves 6-digit codes, 6-digit pairs, Google 4+4 codes, GitHub hyphenated codes, and expands >10 slots", () => {
    const pasted = [
      "1. 0421 8899",
      "2) 123456",
      "3. abcde-12345",
      "4. 123456 789012",
      "5. 000789",
      "6. 111222",
      "7. 333444",
      "8. 555666",
      "9. 777888",
      "10. 999000",
      "11. 121212",
      "12. 343434",
    ].join("\n");

    const parsed = parseRecoveryCodesPaste(pasted);
    expect(parsed).toEqual([
      "0421 8899",
      "123456",
      "abcde-12345",
      "123456 789012",
      "000789",
      "111222",
      "333444",
      "555666",
      "777888",
      "999000",
      "121212",
      "343434",
    ]);

    const applied = applyRecoveryCodesPaste(Array(RECOVERY_CODES_DEFAULT_SLOTS).fill(""), 0, pasted, false);
    expect(applied.slots).toHaveLength(12);
    expect(applied.slots[0]).toBe("0421 8899");
    expect(applied.slots[1]).toBe("123456");
    expect(applied.slots[3]).toBe("123456 789012");
    expect(applied.slots[11]).toBe("343434");

    const serialized = serializeRecoveryCodesSlots(applied.slots);
    const roundTripped = parseRecoveryCodesToSlots(serialized);
    expect(roundTripped).toEqual(parsed);
  });

  it("auto-migrates legacy 20x4 serialized Google backup codes into 10 paired codes", () => {
    const legacy = [
      "0642 0983 7890 4684",
      "9027 0949 6644 9527",
      "3650 1793 1091 1009",
      "7446 0168 5156 5150",
      "3191 9860 3847 8943",
    ].join("\n");

    const migrated = parseRecoveryCodesPaste(legacy);
    expect(migrated).toHaveLength(10);
    expect(migrated[0]).toBe("0642 0983");
    expect(migrated[9]).toBe("3847 8943");
  });

  it("matches existing logins for password updates and passkey registration via local-part and single-account fallback", () => {
    const ciphers: CipherPayload[] = [
      {
        id: "gh-1",
        type: CipherType.Login,
        name: "GitHub Main",
        login: {
          username: "octocat@example.com",
          password: "old-password",
          uris: [{ uri: "https://github.com/login" }],
        },
      },
      {
        id: "work-1",
        type: CipherType.Login,
        name: "Work Portal",
        login: {
          username: "staff-user",
          password: "pw",
          uris: [{ uri: "https://portal.company.dev" }],
        },
      },
    ];

    // Local-part match ("octocat" <-> "octocat@example.com")
    expect(
      findMatchingLoginCipher(ciphers, "https://github.com/settings/security", "octocat")?.id,
    ).toBe("gh-1");

    // Password-only change form without username on a single-login domain
    expect(
      findMatchingLoginCipher(ciphers, "https://github.com/settings/password", "")?.id,
    ).toBe("gh-1");

    // Passkey registration where site passes handle/local-part or completely different label for single login
    const pkMatchLocal = findPasskeyTargetLoginCiphers(
      ciphers,
      "https://github.com",
      "github.com",
      "octocat",
      "The Octocat",
    );
    expect(pkMatchLocal.recommendedId).toBe("gh-1");

    const pkSingleFallback = findPasskeyTargetLoginCiphers(
      ciphers,
      "https://portal.company.dev",
      "company.dev",
      "new-handle-id",
      null,
    );
    expect(pkSingleFallback.recommendedId).toBe("work-1");
  });
});
