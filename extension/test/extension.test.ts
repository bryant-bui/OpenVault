import fs from "node:fs";
import path from "node:path";
import { CipherType, type CipherPayload } from "@openvault/crypto-core";
import { describe, expect, it } from "vitest";
import {
  extractDisplayDomain,
  findMatchingLogins,
  getBaseDomain,
  parseHostname,
  scoreUriMatch,
} from "../src/shared/domain-match.js";
import { classifyField, formatTotpCode, maskCardNumber } from "../src/shared/field-classifier.js";

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
  it("declares valid Manifest V3 with real PNG icons of exact pixel dimensions and no inline scripts", () => {
    const manifestPath = path.join(EXT_ROOT, "public/manifest.json");
    const manifest = JSON.parse(fs.readFileSync(manifestPath, "utf8")) as {
      manifest_version: number;
      icons: Record<string, string>;
      action: { default_popup?: string; default_icon?: Record<string, string> };
      side_panel: { default_path: string };
      background: { service_worker: string };
      permissions: string[];
      content_security_policy: { extension_pages: string };
    };

    expect(manifest.manifest_version).toBe(3);
    expect(manifest.background.service_worker).toBe("background.js");
    expect(manifest.action.default_popup).toBe("popup.html");
    expect(manifest.side_panel.default_path).toBe("sidepanel.html");
    expect(manifest.permissions).toContain("tabs");
    expect(manifest.permissions).toContain("sidePanel");
    expect(manifest.permissions).toContain("offscreen");
    expect(manifest.permissions).toContain("privacy");
    expect(manifest.permissions).toContain("webRequest");
    expect(manifest.permissions).toContain("webRequestAuthProvider");
    expect(manifest.content_security_policy.extension_pages).toContain("wasm-unsafe-eval");

    // Verify offscreen document exists with zero inline scripts
    const offscreenHtml = fs.readFileSync(path.join(EXT_ROOT, "public/offscreen.html"), "utf8");
    expect(offscreenHtml).toContain('src="offscreen.js"');
    expect(offscreenHtml).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/i);
    expect(fs.existsSync(path.join(EXT_ROOT, "public/offscreen.js"))).toBe(true);

    // Verify every icon exists and has valid PNG header + exact declared dimensions
    for (const [sizeStr, relPath] of Object.entries(manifest.icons)) {
      const expected = Number(sizeStr);
      const absPath = path.join(EXT_ROOT, "public", relPath);
      expect(fs.existsSync(absPath)).toBe(true);
      const buf = fs.readFileSync(absPath);
      // PNG signature: 89 50 4E 47 0D 0A 1A 0A
      expect(buf.subarray(0, 8).toString("hex")).toBe("89504e470d0a1a0a");
      const width = buf.readUInt32BE(16);
      const height = buf.readUInt32BE(20);
      expect(width).toBe(expected);
      expect(height).toBe(expected);
    }

    // Verify HTML entrypoints have zero inline scripts or inline event handlers
    for (const htmlFile of ["popup.html", "sidepanel.html"]) {
      const html = fs.readFileSync(path.join(EXT_ROOT, htmlFile), "utf8");
      expect(html).not.toMatch(/<script(?![^>]*\bsrc=)[^>]*>/i);
      expect(html).not.toMatch(/\son(click|load|submit|change)=/i);
    }
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
});
