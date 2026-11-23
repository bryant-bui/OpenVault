import type { InlineFieldKind } from "./types.js";

export interface FieldDescriptor {
  type: string;
  name: string;
  id: string;
  autocomplete: string;
  placeholder: string;
  ariaLabel: string;
  inputMode?: string;
  maxLength?: number;
  formHasPasswordField?: boolean;
  formPasswordCount?: number;
  siblingSingleCharInputCount?: number;
}

const EXCLUDED_INPUT_TYPES = new Set([
  "hidden",
  "submit",
  "button",
  "reset",
  "checkbox",
  "radio",
  "file",
  "image",
  "range",
  "color",
  "date",
  "datetime-local",
  "month",
  "week",
  "time",
  "search",
]);

function combinedHint(d: FieldDescriptor): string {
  return `${d.name} ${d.id} ${d.autocomplete} ${d.placeholder} ${d.ariaLabel}`.toLowerCase();
}

export function isCardHint(autocomplete: string, hint: string): boolean {
  const ac = autocomplete.trim().toLowerCase();
  if (ac.startsWith("cc-")) return true;
  return /(card.?num|cc.?num|ccnum|credit.?card|debit.?card|card.?holder|name.?on.?card|card.?exp|expir(y|ation)|cvv|cvc|csc|security.?code.?card|billing.?card)/.test(
    hint,
  );
}

export function isTotpHint(d: FieldDescriptor, hint: string): boolean {
  if (d.type === "password") return false;
  const ac = d.autocomplete.trim().toLowerCase();
  if (ac === "one-time-code") return true;

  // Split 6-digit OTP input box (maxLength === 1 inside a 4..8 sibling group)
  if (
    d.maxLength === 1 &&
    (d.siblingSingleCharInputCount ?? 0) >= 4 &&
    (d.siblingSingleCharInputCount ?? 0) <= 8
  ) {
    return true;
  }

  if (/(zip|postal|coupon|promo|referral|voucher|discount|captcha|cvv|cvc|csc)/.test(hint)) {
    return false;
  }

  return /(\btotp\b|\b2fa\b|\bmfa\b|\botp\b|one.?time|two.?factor|auth(enticator)?.?code|verif(y|ication).?code|passcode|mã xác (thực|minh)|mã bảo mật|mã 2fa)/.test(
    hint,
  );
}

export function isNewPasswordHint(d: FieldDescriptor, hint: string): boolean {
  if (d.type !== "password") return false;
  const ac = d.autocomplete.trim().toLowerCase();
  if (ac === "new-password") return true;
  if ((d.formPasswordCount ?? 1) >= 2) return true;
  return /(new.?pass|confirm.?pass|repeat.?pass|retype.?pass|create.?pass|signup|sign.?up|register|set.?pass|mật khẩu mới|nhập lại mật khẩu|tạo mật khẩu)/.test(
    hint,
  );
}

export function isIdentityHint(d: FieldDescriptor, hint: string): boolean {
  const ac = d.autocomplete.trim().toLowerCase();
  if (
    /^(name|given-name|additional-name|family-name|tel|tel-national|street-address|address-line1|address-line2|address-level1|address-level2|postal-code|country|country-name|organization)$/.test(
      ac,
    )
  ) {
    return true;
  }
  if (/(search|query|filter|captcha|coupon|promo|comment|message|subject|title|note)/.test(hint)) {
    return false;
  }
  return /(full.?name|first.?name|last.?name|given.?name|family.?name|họ.?tên|họ và tên|số điện thoại|phone.?num|mobile|street.?addr|address.?line|địa chỉ|postal.?code|zip.?code|mã bưu|city|thành phố|province|tỉnh|country|quốc gia|company|công ty|organization)/.test(
    hint,
  );
}

export function isUsernameHint(d: FieldDescriptor, hint: string): boolean {
  if (!["text", "email", "tel"].includes(d.type)) return false;
  if (/(search|query|filter|captcha|otp|totp|2fa|zip|postal|city|address|card|cvv|cvc)/.test(hint)) {
    return false;
  }
  if (d.type === "email") return true;
  const ac = d.autocomplete.trim().toLowerCase();
  if (ac === "username" || ac === "email") return true;
  return /(user|email|login|account|member|uid|identifier|handle|tên đăng nhập|tài khoản)/.test(
    hint,
  );
}

/**
 * Classifies an HTML input field into one of OpenVault's 5 autofill categories,
 * or null if OpenVault should stay out of the way.
 */
export function classifyField(d: FieldDescriptor): InlineFieldKind | null {
  const type = (d.type || "text").toLowerCase();
  if (EXCLUDED_INPUT_TYPES.has(type)) return null;

  const normalized: FieldDescriptor = { ...d, type };
  const hint = combinedHint(normalized);

  // 1. Password fields (split into new-password autosuggest vs login)
  if (type === "password") {
    return isNewPasswordHint(normalized, hint) ? "new-password" : "login";
  }

  // 2. 2FA / TOTP fields
  if (isTotpHint(normalized, hint)) {
    return "totp";
  }

  // 3. Credit card fields
  if (isCardHint(normalized.autocomplete, hint)) {
    return "card";
  }

  // 4. Username / Email in login or standalone forms
  if (isUsernameHint(normalized, hint)) {
    // If inside an explicit checkout/profile form with no password field and identity hints, treat email as login/identity smoothly
    if (!normalized.formHasPasswordField && normalized.type === "email" && isIdentityHint(normalized, hint)) {
      return "identity";
    }
    return "login";
  }

  // 5. Identity fields (only when not inside a simple login form)
  if (!normalized.formHasPasswordField && isIdentityHint(normalized, hint)) {
    return "identity";
  }

  return null;
}

export function formatTotpCode(code: string): string {
  if (code.length === 6) return `${code.slice(0, 3)} ${code.slice(3)}`;
  if (code.length === 8) return `${code.slice(0, 4)} ${code.slice(4)}`;
  return code;
}

export function maskCardNumber(num: string): string {
  const digits = num.replace(/\D/g, "");
  if (digits.length < 4) return num;
  return `•••• ${digits.slice(-4)}`;
}
