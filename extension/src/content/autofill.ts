import {
  classifyField,
  formatTotpCode,
  maskCardNumber,
  type FieldDescriptor,
} from "../shared/field-classifier.js";
import {
  WEBAUTHN_BRIDGE_CHANNEL,
  type PasskeyPrepareCreateResponse,
  type PasskeyPrepareGetResponse,
  type SerializedCreateRequest,
  type SerializedCreateResponseData,
  type SerializedGetRequest,
  type SerializedGetResponseData,
  type SerializedWebAuthnRequest,
  type SerializedWebAuthnResponse,
  type WebAuthnBridgeRequestEnvelope,
  type WebAuthnBridgeResponseEnvelope,
} from "../shared/passkey-bridge.js";
import type {
  AutofillCandidate,
  AutofillContextResponse,
  BgResponse,
  CardAutofillCandidate,
  IdentityAutofillCandidate,
  InlineFieldKind,
  PendingSaveCredential,
  TotpAutofillCandidate,
} from "../shared/types.js";

/**
 * OpenVault Content Script (ISOLATED World) — v0.2.0
 * - 1Password Slide-3 embedded circular keyhole trigger + crisp floating dropdown
 *   inside a CLOSED Shadow DOM (zero CSS bleed, unreachable by page scripts)
 * - Smart autofill for:
 *     1. Login credentials (+ automatic 2FA copy to clipboard)
 *     2. 2FA / TOTP codes (single input AND split 6-box OTP inputs)
 *     3. New-password strong password autosuggest (fills password + confirm password)
 *     4. Credit cards (cardholder, number, expiry MM/YY or separate fields/selects, CVV)
 *     5. Identities (full/first/middle/last name, email, phone, company, address, city, postal, country)
 * - Dispatches native HTMLInputElement/HTMLSelectElement setters + input/change events
 *   for React / Vue / Angular / Svelte compatibility
 * - Captures login form submissions to offer saving new credentials cleanly
 */

const HOST_ATTR = "data-openvault-bound";
const OVERLAY_ID = "openvault-shadow-host";

interface ShadowOverlay {
  host: HTMLDivElement;
  shadow: ShadowRoot;
  container: HTMLDivElement;
}

let overlayRef: ShadowOverlay | null = null;
let activeInput: HTMLInputElement | null = null;
let dropdownOpen = false;
let interactingWithOverlay = false;
let scanQueued = false;

function isVisibleElement(el: HTMLElement): boolean {
  if ((el as HTMLInputElement).disabled || (el as HTMLInputElement).readOnly) return false;
  const rect = el.getBoundingClientRect();
  if (rect.width < 22 || rect.height < 16) return false;
  const style = window.getComputedStyle(el);
  return style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
}

function isVisibleInput(el: HTMLInputElement): boolean {
  if (!isVisibleElement(el)) return false;
  const rect = el.getBoundingClientRect();
  // Allow single-character OTP inputs (~26-48px wide), require >= 60px for standard inputs
  return el.maxLength === 1 ? rect.width >= 22 : rect.width >= 60;
}

function getFieldDescriptor(el: HTMLInputElement): FieldDescriptor {
  const scope: ParentNode = el.form ?? el.parentElement?.parentElement ?? document;
  const formInputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );
  const passwordInputs = formInputs.filter((i) => i.type === "password");

  let siblingSingleCharInputCount = 0;
  if (el.maxLength === 1 && el.parentElement) {
    const groupScope = el.parentElement.parentElement ?? el.parentElement;
    siblingSingleCharInputCount = Array.from(
      groupScope.querySelectorAll<HTMLInputElement>("input"),
    ).filter((i) => isVisibleInput(i) && i.maxLength === 1 && i.type !== "password").length;
  }

  return {
    type: el.type || "text",
    name: el.name || "",
    id: el.id || "",
    autocomplete: el.autocomplete || el.getAttribute("autocomplete") || "",
    placeholder: el.placeholder || "",
    ariaLabel: el.getAttribute("aria-label") || "",
    inputMode: el.inputMode || "",
    maxLength: el.maxLength > 0 ? el.maxLength : undefined,
    formHasPasswordField: passwordInputs.length > 0,
    formPasswordCount: passwordInputs.length,
    siblingSingleCharInputCount,
  };
}

function detectFieldKind(el: HTMLInputElement): InlineFieldKind | null {
  return classifyField(getFieldDescriptor(el));
}

function setNativeInputValue(input: HTMLInputElement, value: string): void {
  const proto = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");
  if (proto?.set) {
    proto.set.call(input, value);
  } else {
    input.value = value;
  }
  input.dispatchEvent(new Event("input", { bubbles: true }));
  input.dispatchEvent(new Event("change", { bubbles: true }));
  input.dispatchEvent(new KeyboardEvent("keyup", { bubbles: true }));
}

function setNativeSelectValue(select: HTMLSelectElement, candidates: string[]): boolean {
  const normalized = candidates.map((c) => c.trim().toLowerCase()).filter(Boolean);
  if (normalized.length === 0) return false;

  for (const opt of Array.from(select.options)) {
    const val = opt.value.trim().toLowerCase();
    const txt = opt.text.trim().toLowerCase();
    if (normalized.includes(val) || normalized.includes(txt)) {
      const proto = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value");
      if (proto?.set) {
        proto.set.call(select, opt.value);
      } else {
        select.value = opt.value;
      }
      select.dispatchEvent(new Event("input", { bubbles: true }));
      select.dispatchEvent(new Event("change", { bubbles: true }));
      return true;
    }
  }
  return false;
}

function fieldHintText(el: HTMLElement): string {
  const input = el as HTMLInputElement;
  const labelText =
    el.id && document.querySelector<HTMLLabelElement>(`label[for="${CSS.escape(el.id)}"]`)
      ?.textContent;
  return `${input.name ?? ""} ${el.id ?? ""} ${el.getAttribute("autocomplete") ?? ""} ${input.placeholder ?? ""} ${el.getAttribute("aria-label") ?? ""} ${labelText ?? ""}`.toLowerCase();
}

function findCompanionLoginFields(anchor: HTMLInputElement): {
  usernameInput: HTMLInputElement | null;
  passwordInput: HTMLInputElement | null;
} {
  const scope: ParentNode = anchor.form ?? document;
  const inputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );

  let passwordInput: HTMLInputElement | null = anchor.type === "password" ? anchor : null;
  let usernameInput: HTMLInputElement | null = anchor.type !== "password" ? anchor : null;

  if (!passwordInput) {
    passwordInput = inputs.find((i) => i.type === "password") ?? null;
  }
  if (!usernameInput && passwordInput) {
    const pwIndex = inputs.indexOf(passwordInput);
    for (let i = pwIndex - 1; i >= 0; i--) {
      const candidate = inputs[i]!;
      const kind = detectFieldKind(candidate);
      if (kind === "login" || candidate.type === "text" || candidate.type === "email") {
        usernameInput = candidate;
        break;
      }
    }
  }

  return { usernameInput, passwordInput };
}

function fillLoginFields(
  username: string,
  password: string,
  anchor?: HTMLInputElement | null,
): boolean {
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );
  const start =
    anchor ??
    inputs.find((i) => i.type === "password") ??
    inputs.find((i) => detectFieldKind(i) === "login") ??
    null;
  if (!start) return false;

  const { usernameInput, passwordInput } = findCompanionLoginFields(start);
  let filled = false;

  if (usernameInput && username) {
    setNativeInputValue(usernameInput, username);
    filled = true;
  }
  if (passwordInput && password) {
    setNativeInputValue(passwordInput, password);
    filled = true;
  }

  return filled;
}

function fillNewPasswordFields(password: string, anchor: HTMLInputElement): boolean {
  const scope: ParentNode = anchor.form ?? document;
  const passwordInputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(
    (i) => isVisibleInput(i) && i.type === "password",
  );
  const anchorIdx = passwordInputs.indexOf(anchor);
  if (anchorIdx === -1) {
    setNativeInputValue(anchor, password);
    return true;
  }

  // Fill the focused password field plus an adjacent confirmation password field if present
  setNativeInputValue(anchor, password);
  const nextPw = passwordInputs[anchorIdx + 1];
  if (nextPw) {
    const nextHint = fieldHintText(nextPw);
    const isCurrentField = /(current|old|existing|hiện tại|cũ)/.test(nextHint);
    if (!isCurrentField) {
      setNativeInputValue(nextPw, password);
    }
  }
  return true;
}

function fillTotpField(code: string, anchor: HTMLInputElement): boolean {
  // Handle split single-digit OTP boxes (e.g., 6 separate <input maxlength="1"> elements)
  if (anchor.maxLength === 1 && anchor.parentElement) {
    const container = anchor.parentElement.parentElement ?? anchor.parentElement;
    const boxes = Array.from(container.querySelectorAll<HTMLInputElement>("input")).filter(
      (i) => isVisibleInput(i) && i.maxLength === 1 && i.type !== "password",
    );
    if (boxes.length >= 4 && boxes.length <= 8) {
      const startIndex = Math.max(0, boxes.indexOf(anchor));
      const targets = boxes.length === code.length ? boxes : boxes.slice(startIndex);
      for (let i = 0; i < Math.min(code.length, targets.length); i++) {
        setNativeInputValue(targets[i]!, code[i]!);
      }
      targets[Math.min(code.length, targets.length) - 1]?.focus();
      return true;
    }
  }

  setNativeInputValue(anchor, code);
  return true;
}

function fillCardFields(
  card: CardAutofillCandidate,
  anchor?: HTMLInputElement | null,
): boolean {
  const scope: ParentNode = anchor?.form ?? document;
  const inputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );
  const selects = Array.from(scope.querySelectorAll<HTMLSelectElement>("select")).filter(
    isVisibleElement,
  );

  const monthPadded = card.expMonth ? card.expMonth.replace(/\D/g, "").padStart(2, "0") : "";
  const monthRaw = monthPadded ? String(Number(monthPadded)) : "";
  const yearFull = card.expYear
    ? card.expYear.replace(/\D/g, "").length === 2
      ? `20${card.expYear.replace(/\D/g, "")}`
      : card.expYear.replace(/\D/g, "")
    : "";
  const yearShort = yearFull.slice(-2);

  let filledCount = 0;

  for (const input of inputs) {
    const ac = (input.autocomplete || "").trim().toLowerCase();
    const hint = fieldHintText(input);

    if (
      card.number &&
      (ac === "cc-number" || /(card.?num|cc.?num|ccnum|credit.?card.?num|số thẻ)/.test(hint))
    ) {
      setNativeInputValue(input, card.number);
      filledCount++;
    } else if (
      card.cardholderName &&
      (ac === "cc-name" || /(card.?holder|name.?on.?card|chủ thẻ)/.test(hint))
    ) {
      setNativeInputValue(input, card.cardholderName);
      filledCount++;
    } else if (card.code && (ac === "cc-csc" || /(\bcvv\b|\bcvc\b|\bcsc\b|security.?code)/.test(hint))) {
      setNativeInputValue(input, card.code);
      filledCount++;
    } else if (
      monthPadded &&
      yearShort &&
      (ac === "cc-exp" || /(expir|mm\s*\/\s*yy|card.?exp|ngày hết hạn)/.test(hint)) &&
      !/(month|tháng|year|năm)/.test(hint)
    ) {
      setNativeInputValue(input, `${monthPadded}/${yearShort}`);
      filledCount++;
    } else if (monthPadded && (ac === "cc-exp-month" || /(exp.?month|card.?month|tháng)/.test(hint))) {
      setNativeInputValue(input, monthPadded);
      filledCount++;
    } else if (yearFull && (ac === "cc-exp-year" || /(exp.?year|card.?year|năm)/.test(hint))) {
      setNativeInputValue(input, input.maxLength === 2 ? yearShort : yearFull);
      filledCount++;
    }
  }

  for (const select of selects) {
    const ac = (select.autocomplete || "").trim().toLowerCase();
    const hint = fieldHintText(select);
    if (monthPadded && (ac === "cc-exp-month" || /(month|tháng)/.test(hint))) {
      if (setNativeSelectValue(select, [monthPadded, monthRaw])) filledCount++;
    } else if (yearFull && (ac === "cc-exp-year" || /(year|năm)/.test(hint))) {
      if (setNativeSelectValue(select, [yearFull, yearShort])) filledCount++;
    }
  }

  // Fallback: if focused directly on a single unknown card input, at least fill card number
  if (filledCount === 0 && anchor && card.number) {
    setNativeInputValue(anchor, card.number);
    return true;
  }

  return filledCount > 0;
}

function fillIdentityFields(
  identity: IdentityAutofillCandidate,
  anchor?: HTMLInputElement | null,
): boolean {
  const scope: ParentNode = anchor?.form ?? document;
  const inputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );
  let filledCount = 0;

  for (const input of inputs) {
    if (input.type === "password") continue;
    const ac = (input.autocomplete || "").trim().toLowerCase();
    const hint = fieldHintText(input);

    if (identity.email && (ac === "email" || input.type === "email" || /\bemail\b/.test(hint))) {
      setNativeInputValue(input, identity.email);
      filledCount++;
    } else if (
      identity.phone &&
      (ac === "tel" ||
        ac === "tel-national" ||
        input.type === "tel" ||
        /(phone|mobile|số điện thoại|điện thoại)/.test(hint))
    ) {
      setNativeInputValue(input, identity.phone);
      filledCount++;
    } else if (
      identity.firstName &&
      (ac === "given-name" || /(first.?name|given.?name|\bhọ\b(?!.*tên))/.test(hint))
    ) {
      setNativeInputValue(input, identity.firstName);
      filledCount++;
    } else if (
      identity.lastName &&
      (ac === "family-name" || /(last.?name|family.?name|surname)/.test(hint))
    ) {
      setNativeInputValue(input, identity.lastName);
      filledCount++;
    } else if (
      identity.fullName &&
      (ac === "name" || /(full.?name|your.?name|họ.?tên|họ và tên)/.test(hint))
    ) {
      setNativeInputValue(input, identity.fullName);
      filledCount++;
    } else if (
      identity.company &&
      (ac === "organization" || /(company|organization|công ty)/.test(hint))
    ) {
      setNativeInputValue(input, identity.company);
      filledCount++;
    } else if (
      identity.address2 &&
      (ac === "address-line2" || /(address.?2|apt|suite|tầng|căn hộ)/.test(hint))
    ) {
      setNativeInputValue(input, identity.address2);
      filledCount++;
    } else if (
      identity.address1 &&
      (ac === "street-address" ||
        ac === "address-line1" ||
        /(street|address|địa chỉ)/.test(hint))
    ) {
      setNativeInputValue(input, identity.address1);
      filledCount++;
    } else if (
      identity.city &&
      (ac === "address-level2" || /(city|thành phố|quận|huyện)/.test(hint))
    ) {
      setNativeInputValue(input, identity.city);
      filledCount++;
    } else if (
      identity.state &&
      (ac === "address-level1" || /(state|province|tỉnh|bang)/.test(hint))
    ) {
      setNativeInputValue(input, identity.state);
      filledCount++;
    } else if (
      identity.postalCode &&
      (ac === "postal-code" || /(postal|zip|mã bưu)/.test(hint))
    ) {
      setNativeInputValue(input, identity.postalCode);
      filledCount++;
    } else if (
      identity.country &&
      (ac === "country" || ac === "country-name" || /(country|quốc gia)/.test(hint))
    ) {
      setNativeInputValue(input, identity.country);
      filledCount++;
    }
  }

  return filledCount > 0;
}

// --- Closed Shadow DOM UI (1Password Slide-3 Aesthetic) ---

function ensureOverlay(): ShadowOverlay {
  if (overlayRef && document.documentElement.contains(overlayRef.host)) {
    return overlayRef;
  }

  const host = document.createElement("div");
  host.id = OVERLAY_ID;
  host.style.cssText = "position:fixed;inset:0;pointer-events:none;z-index:2147483647;";

  const shadow = host.attachShadow({ mode: "closed" });
  const style = document.createElement("style");
  style.textContent = `
    :host { all: initial; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Inter, sans-serif; }
    * { box-sizing: border-box; }

    .ov-inline-trigger {
      position: fixed;
      pointer-events: auto;
      width: 22px;
      height: 22px;
      border-radius: 999px;
      border: 0;
      padding: 0;
      background: linear-gradient(135deg, #0A84FF, #0455BF);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      box-shadow: 0 2px 6px rgba(5, 114, 236, 0.35);
      transition: transform 110ms ease, box-shadow 110ms ease;
    }
    .ov-inline-trigger:hover {
      transform: scale(1.08);
      box-shadow: 0 3px 10px rgba(5, 114, 236, 0.48);
    }
    .ov-inline-trigger .ov-tip {
      position: absolute;
      bottom: calc(100% + 7px);
      right: -8px;
      background: #0a101d;
      color: #ffffff;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 8px;
      border-radius: 6px;
      white-space: nowrap;
      opacity: 0;
      pointer-events: none;
      transform: translateY(2px);
      transition: opacity 120ms ease, transform 120ms ease;
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.3);
    }
    .ov-inline-trigger .ov-tip::after {
      content: "";
      position: absolute;
      top: 100%;
      right: 14px;
      border: 5px solid transparent;
      border-top-color: #0a101d;
    }
    .ov-inline-trigger:hover .ov-tip {
      opacity: 1;
      transform: translateY(0);
    }

    .ov-dropdown {
      position: fixed;
      pointer-events: auto;
      min-width: 274px;
      max-width: 350px;
      background: #ffffff;
      color: #0a1a33;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      box-shadow: 0 12px 32px rgba(10, 26, 51, 0.16), 0 2px 6px rgba(10, 26, 51, 0.06);
      overflow: hidden;
      animation: ovPop 130ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ov-header {
      padding: 7px 11px 4px;
      font-size: 10.5px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #64748b;
    }
    .ov-list { max-height: 248px; overflow-y: auto; padding: 5px; }
    .ov-item {
      width: 100%;
      border: 0;
      background: transparent;
      color: inherit;
      text-align: left;
      padding: 7px 9px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      transition: background 110ms ease;
    }
    .ov-item:hover { background: #0572EC; color: #ffffff; }
    .ov-item:hover .ov-sub { color: rgba(255, 255, 255, 0.85); }
    .ov-item:hover .ov-badge { background: rgba(255, 255, 255, 0.22); color: #ffffff; }
    .ov-item:hover .ov-code { color: #ffffff; }

    .ov-avatar {
      width: 30px;
      height: 30px;
      border-radius: 8px;
      background: linear-gradient(135deg, #0A84FF, #0455BF);
      color: #fff;
      font-weight: 700;
      font-size: 13px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .ov-avatar-emerald { background: linear-gradient(135deg, #10B981, #047857); }
    .ov-avatar-violet { background: linear-gradient(135deg, #8B5CF6, #6D28D9); }
    .ov-avatar-amber { background: linear-gradient(135deg, #F59E0B, #B45309); }

    .ov-meta { min-width: 0; flex: 1; }
    .ov-name { font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ov-sub { font-size: 11.5px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 1px; }
    .ov-code {
      font-family: ui-monospace, "Cascadia Code", Menlo, Consolas, monospace;
      font-size: 12.5px;
      font-weight: 700;
      letter-spacing: 0.04em;
      color: #0572EC;
      margin-top: 2px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ov-badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 6px;
      background: #ebf4ff;
      color: #0572EC;
      flex-shrink: 0;
    }
    .ov-suggest-row {
      display: flex;
      align-items: center;
      gap: 4px;
    }
    .ov-refresh-btn {
      width: 28px;
      height: 28px;
      border: 0;
      border-radius: 7px;
      background: #f1f5f9;
      color: #334155;
      cursor: pointer;
      font-size: 13px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      margin-right: 4px;
    }
    .ov-refresh-btn:hover { background: #e2e8f0; }
    .ov-divider { height: 1px; background: #e2e8f0; margin: 4px 8px; }
    .ov-empty { padding: 12px 14px; font-size: 12px; color: #5b687c; text-align: center; line-height: 1.45; }

    /* Inline Unlock Popup (PIN 3+3 auto-unlock & Master Password directly next to form field) */
    .ov-unlock-box {
      padding: 12px 13px 11px;
      display: flex;
      flex-direction: column;
      gap: 9px;
    }
    .ov-unlock-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .ov-unlock-brand {
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
    }
    .ov-unlock-logo {
      width: 24px;
      height: 24px;
      border-radius: 7px;
      background: linear-gradient(135deg, #0A84FF, #0455BF);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .ov-unlock-title {
      font-size: 12.5px;
      font-weight: 700;
      line-height: 1.2;
      color: inherit;
    }
    .ov-unlock-email {
      font-size: 11px;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 195px;
    }
    .ov-unlock-close {
      width: 22px;
      height: 22px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      font-size: 14px;
      line-height: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .ov-unlock-close:hover { background: #f1f5f9; color: #0a1a33; }

    .ov-unlock-pin-label {
      font-size: 11.5px;
      color: #5b687c;
      text-align: center;
      font-weight: 500;
    }
    .ov-unlock-pin-wrap {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      cursor: text;
      padding: 2px 0;
    }
    .ov-unlock-pin-native {
      position: absolute;
      inset: 0;
      width: 100%;
      height: 100%;
      opacity: 0;
      cursor: text;
      z-index: 2;
      font-size: 16px;
    }
    .ov-unlock-pin-slots {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      pointer-events: none;
    }
    .ov-unlock-pin-gap { width: 8px; }
    .ov-unlock-pin-slot {
      width: 34px;
      height: 40px;
      border-radius: 9px;
      border: 1.5px solid #cbd5e1;
      background: #f8fafc;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: border-color 120ms ease, box-shadow 120ms ease, background 120ms ease;
    }
    .ov-unlock-pin-slot[data-active="true"] {
      border-color: #0572EC;
      background: #ffffff;
      box-shadow: 0 0 0 2.5px rgba(5, 114, 236, 0.22);
    }
    .ov-unlock-pin-slot[data-filled="true"] {
      border-color: #93c5fd;
    }
    .ov-unlock-pin-slot[data-error="true"] {
      border-color: #e11d48;
      box-shadow: 0 0 0 2px rgba(225, 29, 72, 0.2);
    }
    .ov-unlock-pin-dot {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: #0a1a33;
    }
    .ov-unlock-pin-caret {
      width: 1.5px;
      height: 16px;
      border-radius: 999px;
      background: #0572EC;
    }
    .ov-unlock-shake {
      animation: ovShake 320ms cubic-bezier(0.36, 0.07, 0.19, 0.97);
    }

    .ov-unlock-form {
      display: flex;
      gap: 6px;
      align-items: center;
    }
    .ov-unlock-input-wrap {
      position: relative;
      flex: 1;
      min-width: 0;
    }
    .ov-unlock-input {
      width: 100%;
      height: 34px;
      padding: 0 30px 0 10px;
      border-radius: 8px;
      border: 1.5px solid #cbd5e1;
      background: #f8fafc;
      color: #0a1a33;
      font-size: 12.5px;
      outline: none;
    }
    .ov-unlock-input:focus {
      border-color: #0572EC;
      background: #ffffff;
      box-shadow: 0 0 0 2.5px rgba(5, 114, 236, 0.18);
    }
    .ov-unlock-eye {
      position: absolute;
      right: 4px;
      top: 50%;
      transform: translateY(-50%);
      width: 24px;
      height: 24px;
      border: 0;
      border-radius: 6px;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .ov-unlock-submit {
      height: 34px;
      padding: 0 12px;
      border: 0;
      border-radius: 8px;
      background: #0572EC;
      color: #ffffff;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      flex-shrink: 0;
    }
    .ov-unlock-submit:hover:not(:disabled) { background: #045DC2; }
    .ov-unlock-submit:disabled { opacity: 0.55; cursor: not-allowed; }
    .ov-unlock-err {
      font-size: 11.5px;
      color: #e11d48;
      text-align: center;
      line-height: 1.35;
    }
    .ov-unlock-footer {
      display: flex;
      justify-content: center;
    }
    .ov-unlock-link {
      border: 0;
      background: transparent;
      color: #0572EC;
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
      padding: 2px 6px;
      border-radius: 6px;
    }
    .ov-unlock-link:hover { text-decoration: underline; }

    /* Save Password Banner */
    .ov-banner {
      position: fixed;
      top: 16px;
      right: 16px;
      width: 330px;
      pointer-events: auto;
      background: #ffffff;
      color: #0a1a33;
      border: 1px solid #d5e3f5;
      border-radius: 12px;
      padding: 14px 16px;
      box-shadow: 0 18px 42px rgba(10, 26, 51, 0.18);
      animation: ovPop 170ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ov-banner-title { font-size: 13.5px; font-weight: 700; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
    .ov-dot {
      width: 9px;
      height: 9px;
      border-radius: 999px;
      background: #0572EC;
      box-shadow: 0 0 0 3px rgba(5, 114, 236, 0.2);
    }
    .ov-banner-sub { font-size: 12px; color: #5b687c; margin-bottom: 12px; word-break: break-all; }
    .ov-actions { display: flex; gap: 8px; justify-content: flex-end; }
    .ov-btn { border: 0; border-radius: 8px; padding: 7px 13px; font-size: 12px; font-weight: 600; cursor: pointer; }
    .ov-btn-primary { background: #0572EC; color: #ffffff; }
    .ov-btn-primary:hover { background: #045DC2; }
    .ov-btn-ghost { background: #f1f5f9; color: #334155; }

    .ov-toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      pointer-events: auto;
      background: #0a1a33;
      color: #ffffff;
      padding: 9px 14px;
      border-radius: 10px;
      font-size: 12.5px;
      font-weight: 500;
      box-shadow: 0 12px 28px rgba(10, 26, 51, 0.35);
      animation: ovPop 140ms ease;
    }

    /* 1Password Slide-3 Passkey Prompt Card */
    .ov-pk-card {
      position: fixed;
      top: 16px;
      right: 16px;
      width: 356px;
      max-width: calc(100vw - 24px);
      pointer-events: auto;
      background: #ffffff;
      color: #0a1a33;
      border: 1px solid #e2e8f0;
      border-radius: 14px;
      padding: 13px 14px 12px;
      box-shadow: 0 20px 48px rgba(10, 26, 51, 0.2), 0 4px 12px rgba(10, 26, 51, 0.08);
      animation: ovPop 160ms cubic-bezier(0.16, 1, 0.3, 1);
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .ov-pk-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .ov-pk-brand {
      display: flex;
      align-items: center;
      gap: 9px;
      min-width: 0;
    }
    .ov-pk-logo {
      width: 24px;
      height: 24px;
      border-radius: 999px;
      background: linear-gradient(135deg, #0A84FF, #0455BF);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 2px 6px rgba(5, 114, 236, 0.3);
    }
    .ov-pk-title {
      font-size: 13.5px;
      font-weight: 700;
      color: inherit;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ov-pk-head-actions {
      display: flex;
      align-items: center;
      gap: 3px;
      flex-shrink: 0;
    }
    .ov-pk-icon-btn {
      width: 26px;
      height: 26px;
      border: 0;
      border-radius: 7px;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      line-height: 1;
    }
    .ov-pk-icon-btn:hover { background: #f1f5f9; color: #0a1a33; }
    .ov-pk-list {
      display: flex;
      flex-direction: column;
      gap: 7px;
      max-height: 230px;
      overflow-y: auto;
    }
    .ov-pk-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 11px;
      border-radius: 11px;
      background: linear-gradient(135deg, #fbfaff, #f3efff);
      border: 1px solid #e6ddff;
    }
    .ov-pk-site-icon {
      width: 34px;
      height: 34px;
      border-radius: 9px;
      background: #111827;
      color: #ffffff;
      font-size: 14px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .ov-pk-meta {
      min-width: 0;
      flex: 1;
    }
    .ov-pk-rp {
      font-size: 13px;
      font-weight: 700;
      color: #0f172a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ov-pk-user {
      font-size: 11.5px;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      margin-top: 1px;
    }
    .ov-pk-btn {
      border: 0;
      border-radius: 8px;
      padding: 7px 12px;
      background: #7253ED;
      color: #ffffff;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 5px;
      flex-shrink: 0;
      transition: background 110ms ease, transform 80ms ease;
    }
    .ov-pk-btn:hover:not(:disabled) { background: #5E3EE0; }
    .ov-pk-btn:active:not(:disabled) { transform: scale(0.98); }
    .ov-pk-btn:disabled { opacity: 0.6; cursor: not-allowed; }
    .ov-pk-warn {
      font-size: 11.5px;
      color: #b45309;
      background: #fffbeb;
      border: 1px solid #fde68a;
      border-radius: 8px;
      padding: 7px 10px;
      line-height: 1.4;
    }
    .ov-pk-err {
      font-size: 11.5px;
      color: #e11d48;
      background: #fff1f2;
      border: 1px solid #fecdd3;
      border-radius: 8px;
      padding: 7px 10px;
      line-height: 1.4;
    }
    .ov-pk-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding-top: 2px;
    }
    .ov-pk-domain {
      font-size: 11px;
      color: #64748b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ov-pk-fallback-link {
      border: 0;
      background: transparent;
      color: #64748b;
      font-size: 11.5px;
      font-weight: 600;
      cursor: pointer;
      padding: 3px 6px;
      border-radius: 6px;
      flex-shrink: 0;
    }
    .ov-pk-fallback-link:hover { background: #f1f5f9; color: #0a1a33; }

    @media (prefers-color-scheme: dark) {
      .ov-pk-card {
        background: #161b24;
        color: #f1f5f9;
        border-color: #263144;
        box-shadow: 0 22px 52px rgba(0, 0, 0, 0.62);
      }
      .ov-pk-icon-btn { color: #94a3b8; }
      .ov-pk-icon-btn:hover { background: #222a3a; color: #f1f5f9; }
      .ov-pk-row {
        background: linear-gradient(135deg, #1d1a2e, #241f3d);
        border-color: #3a3066;
      }
      .ov-pk-site-icon { background: #2b3447; }
      .ov-pk-rp { color: #f8fafc; }
      .ov-pk-user, .ov-pk-domain, .ov-pk-fallback-link { color: #94a3b8; }
      .ov-pk-fallback-link:hover { background: #222a3a; color: #f8fafc; }
      .ov-pk-warn { background: #2b2111; border-color: #78350f; color: #fcd34d; }
      .ov-pk-err { background: #2c141c; border-color: #881337; color: #fda4af; }
      .ov-dropdown {
        background: #161b24;
        color: #f1f5f9;
        border-color: #263144;
        box-shadow: 0 16px 38px rgba(0, 0, 0, 0.55);
      }
      .ov-header, .ov-sub, .ov-empty { color: #94a3b8; }
      .ov-code { color: #60a5fa; }
      .ov-badge { background: #172a4a; color: #60a5fa; }
      .ov-divider { background: #263144; }
      .ov-refresh-btn { background: #222a3a; color: #e2e8f0; }
      .ov-refresh-btn:hover { background: #2d384c; }
      .ov-banner {
        background: #161b24;
        color: #f1f5f9;
        border-color: #254880;
        box-shadow: 0 18px 46px rgba(0, 0, 0, 0.55);
      }
      .ov-banner-sub { color: #94a3b8; }
      .ov-btn-ghost { background: #222a3a; color: #e2e8f0; }
      .ov-unlock-email, .ov-unlock-pin-label, .ov-unlock-close { color: #94a3b8; }
      .ov-unlock-close:hover { background: #222a3a; color: #f1f5f9; }
      .ov-unlock-pin-slot { border-color: #334155; background: #1e2534; }
      .ov-unlock-pin-slot[data-active="true"] { border-color: #60a5fa; background: #232c3e; box-shadow: 0 0 0 2.5px rgba(96, 165, 250, 0.25); }
      .ov-unlock-pin-dot { background: #f8fafc; }
      .ov-unlock-pin-caret { background: #60a5fa; }
      .ov-unlock-input { border-color: #334155; background: #1e2534; color: #f1f5f9; }
      .ov-unlock-input:focus { border-color: #60a5fa; background: #161b24; }
      .ov-unlock-link { color: #60a5fa; }
      .ov-unlock-err { color: #fb7185; }
    }

    @keyframes ovPop {
      from { opacity: 0; transform: translateY(-3px) scale(0.985); }
      to { opacity: 1; transform: translateY(0) scale(1); }
    }
    @keyframes ovShake {
      0%, 100% { transform: translateX(0); }
      20% { transform: translateX(-6px); }
      40% { transform: translateX(6px); }
      60% { transform: translateX(-4px); }
      80% { transform: translateX(3px); }
    }
  `;

  const container = document.createElement("div");
  shadow.appendChild(style);
  shadow.appendChild(container);
  document.documentElement.appendChild(host);

  overlayRef = { host, shadow, container };
  return overlayRef;
}

function hideDropdown(): void {
  dropdownOpen = false;
  interactingWithOverlay = false;
  if (!overlayRef) return;
  overlayRef.container.querySelector(".ov-dropdown")?.remove();
}

function stopPageKeyLeak(el: HTMLElement): void {
  for (const evtName of ["keydown", "keyup", "keypress", "input", "beforeinput"]) {
    el.addEventListener(evtName, (e) => {
      if (evtName === "keydown" && (e as KeyboardEvent).key === "Escape") {
        e.stopPropagation();
        hideDropdown();
        return;
      }
      e.stopPropagation();
    });
  }
}

function createKeyholeSvg(size = 13): SVGSVGElement {
  const svgNS = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNS, "svg");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  const circle = document.createElementNS(svgNS, "circle");
  circle.setAttribute("cx", "12");
  circle.setAttribute("cy", "10");
  circle.setAttribute("r", "4");
  circle.setAttribute("fill", "#ffffff");
  const stem = document.createElementNS(svgNS, "path");
  stem.setAttribute("d", "M10 13h4l1 7H9l1-7z");
  stem.setAttribute("fill", "#ffffff");
  svg.appendChild(circle);
  svg.appendChild(stem);
  return svg;
}

interface UnlockWidgetOptions {
  hasAccount: boolean;
  email: string | null;
  pinAvailableNow: boolean;
  pinLength: number;
  autoFocusField: boolean;
  showHeader?: boolean;
  onClose?: () => void;
  onUnlocked: () => Promise<void>;
}

function createUnlockWidget(opts: UnlockWidgetOptions): HTMLDivElement {
  const box = document.createElement("div");
  box.className = "ov-unlock-box";
  if (opts.showHeader === false) {
    box.style.padding = "4px 2px 2px";
  }
  box.addEventListener("pointerdown", () => {
    interactingWithOverlay = true;
  });

  if (!opts.hasAccount) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent =
      "Chưa đăng nhập OpenVault — nhấn biểu tượng OpenVault trên thanh công cụ để đăng nhập.";
    box.appendChild(empty);
    return box;
  }

  let mode: "pin" | "master" = opts.pinAvailableNow ? "pin" : "master";
  let pinAvailable = opts.pinAvailableNow;
  const pinLen = Math.min(12, Math.max(4, opts.pinLength || 6));
  let busy = false;
  let errorText = "";

  if (opts.showHeader !== false) {
    const head = document.createElement("div");
    head.className = "ov-unlock-head";

    const brand = document.createElement("div");
    brand.className = "ov-unlock-brand";
    const logo = document.createElement("div");
    logo.className = "ov-unlock-logo";
    logo.appendChild(createKeyholeSvg(13));
    const titleWrap = document.createElement("div");
    const titleEl = document.createElement("div");
    titleEl.className = "ov-unlock-title";
    titleEl.textContent = "Mở khóa OpenVault";
    const emailEl = document.createElement("div");
    emailEl.className = "ov-unlock-email";
    emailEl.textContent = opts.email ?? "";
    titleWrap.appendChild(titleEl);
    if (opts.email) titleWrap.appendChild(emailEl);
    brand.appendChild(logo);
    brand.appendChild(titleWrap);

    const closeBtn = document.createElement("button");
    closeBtn.type = "button";
    closeBtn.className = "ov-unlock-close";
    closeBtn.setAttribute("aria-label", "Đóng");
    closeBtn.textContent = "×";
    closeBtn.addEventListener("click", () => {
      if (opts.onClose) {
        opts.onClose();
      } else {
        hideDropdown();
      }
    });

    head.appendChild(brand);
    head.appendChild(closeBtn);
    box.appendChild(head);
  }

  const bodySlot = document.createElement("div");
  bodySlot.style.cssText = "display:flex;flex-direction:column;gap:8px;";
  box.appendChild(bodySlot);

  async function onUnlockSucceeded(): Promise<void> {
    await opts.onUnlocked();
  }

  function renderBody(shouldFocus: boolean): void {
    bodySlot.textContent = "";

    if (mode === "pin" && pinAvailable) {
      let pinValue = "";
      let isFocused = false;

      const label = document.createElement("div");
      label.className = "ov-unlock-pin-label";
      label.textContent = `Nhập mã PIN (${pinLen} số) để mở khóa ngay`;
      bodySlot.appendChild(label);

      const wrap = document.createElement("div");
      wrap.className = "ov-unlock-pin-wrap";

      const nativeInput = document.createElement("input");
      nativeInput.type = "password";
      nativeInput.inputMode = "numeric";
      nativeInput.autocomplete = "one-time-code";
      nativeInput.maxLength = pinLen;
      nativeInput.className = "ov-unlock-pin-native";
      nativeInput.setAttribute("aria-label", `Nhập mã PIN ${pinLen} số`);
      stopPageKeyLeak(nativeInput);

      const slotsRow = document.createElement("div");
      slotsRow.className = "ov-unlock-pin-slots";
      const splitAt = pinLen === 6 ? 3 : -1;
      const slotEls: HTMLDivElement[] = [];

      for (let i = 0; i < pinLen; i++) {
        if (i === splitAt) {
          const gap = document.createElement("span");
          gap.className = "ov-unlock-pin-gap";
          slotsRow.appendChild(gap);
        }
        const slot = document.createElement("div");
        slot.className = "ov-unlock-pin-slot";
        slotsRow.appendChild(slot);
        slotEls.push(slot);
      }

      const errEl = document.createElement("div");
      errEl.className = "ov-unlock-err";
      errEl.style.display = errorText ? "block" : "none";
      errEl.textContent = errorText;

      const syncSlots = () => {
        for (let i = 0; i < slotEls.length; i++) {
          const slot = slotEls[i]!;
          const filled = i < pinValue.length;
          const active = isFocused && !busy && i === Math.min(pinValue.length, pinLen - 1) && !filled;
          slot.dataset.filled = String(filled);
          slot.dataset.active = String(active);
          slot.dataset.error = String(Boolean(errorText));
          slot.textContent = "";
          if (filled) {
            const dot = document.createElement("span");
            dot.className = "ov-unlock-pin-dot";
            slot.appendChild(dot);
          } else if (active) {
            const caret = document.createElement("span");
            caret.className = "ov-unlock-pin-caret";
            slot.appendChild(caret);
          }
        }
      };

      const submitPin = async (code: string) => {
        if (busy) return;
        busy = true;
        errorText = "";
        errEl.style.display = "none";
        label.textContent = "Đang giải mã kho...";
        nativeInput.disabled = true;
        syncSlots();

        try {
          const res = (await chrome.runtime.sendMessage({
            type: "UNLOCK_WITH_PIN",
            pin: code,
          })) as BgResponse;
          if (res?.ok) {
            await onUnlockSucceeded();
            return;
          }
          errorText = res?.error || "Mã PIN không chính xác.";
          if (errorText.includes("hủy mã PIN") || errorText.includes("chưa sẵn sàng")) {
            pinAvailable = false;
            mode = "master";
            busy = false;
            renderBody(true);
            return;
          }
        } catch {
          errorText = "Không thể kết nối OpenVault.";
        }

        busy = false;
        pinValue = "";
        nativeInput.disabled = false;
        nativeInput.value = "";
        label.textContent = `Nhập mã PIN (${pinLen} số) để mở khóa ngay`;
        errEl.textContent = errorText;
        errEl.style.display = "block";
        slotsRow.classList.remove("ov-unlock-shake");
        void slotsRow.offsetWidth;
        slotsRow.classList.add("ov-unlock-shake");
        syncSlots();
        window.requestAnimationFrame(() => nativeInput.focus());
      };

      nativeInput.addEventListener("focus", () => {
        isFocused = true;
        interactingWithOverlay = true;
        syncSlots();
      });
      nativeInput.addEventListener("blur", () => {
        isFocused = false;
        syncSlots();
      });
      nativeInput.addEventListener("input", () => {
        const cleaned = nativeInput.value.replace(/\D/g, "").slice(0, pinLen);
        nativeInput.value = cleaned;
        pinValue = cleaned;
        if (errorText) {
          errorText = "";
          errEl.style.display = "none";
        }
        syncSlots();
        if (cleaned.length === pinLen) {
          void submitPin(cleaned);
        }
      });

      wrap.addEventListener("click", () => nativeInput.focus());
      wrap.appendChild(nativeInput);
      wrap.appendChild(slotsRow);
      bodySlot.appendChild(wrap);
      bodySlot.appendChild(errEl);

      const footer = document.createElement("div");
      footer.className = "ov-unlock-footer";
      const switchBtn = document.createElement("button");
      switchBtn.type = "button";
      switchBtn.className = "ov-unlock-link";
      switchBtn.textContent = "Dùng mật khẩu chính";
      switchBtn.addEventListener("click", () => {
        mode = "master";
        errorText = "";
        renderBody(true);
      });
      footer.appendChild(switchBtn);
      bodySlot.appendChild(footer);

      syncSlots();
      if (shouldFocus) {
        window.requestAnimationFrame(() => nativeInput.focus());
      }
    } else {
      const form = document.createElement("form");
      form.className = "ov-unlock-form";

      const inputWrap = document.createElement("div");
      inputWrap.className = "ov-unlock-input-wrap";

      const pwInput = document.createElement("input");
      pwInput.type = "password";
      pwInput.placeholder = "Nhập mật khẩu chính...";
      pwInput.className = "ov-unlock-input";
      pwInput.autocomplete = "current-password";
      stopPageKeyLeak(pwInput);
      pwInput.addEventListener("focus", () => {
        interactingWithOverlay = true;
      });

      const eyeBtn = document.createElement("button");
      eyeBtn.type = "button";
      eyeBtn.className = "ov-unlock-eye";
      eyeBtn.title = "Hiện/Ẩn mật khẩu";
      eyeBtn.textContent = "👁";
      eyeBtn.addEventListener("click", () => {
        pwInput.type = pwInput.type === "password" ? "text" : "password";
        pwInput.focus();
      });

      inputWrap.appendChild(pwInput);
      inputWrap.appendChild(eyeBtn);

      const submitBtn = document.createElement("button");
      submitBtn.type = "submit";
      submitBtn.className = "ov-unlock-submit";
      submitBtn.textContent = "Mở khóa";

      const errEl = document.createElement("div");
      errEl.className = "ov-unlock-err";
      errEl.style.display = errorText ? "block" : "none";
      errEl.textContent = errorText;

      form.addEventListener("submit", (e) => {
        e.preventDefault();
        const pw = pwInput.value;
        if (!pw || busy) return;
        busy = true;
        errorText = "";
        errEl.style.display = "none";
        submitBtn.disabled = true;
        submitBtn.textContent = "Đang mở...";

        void (async () => {
          try {
            const res = (await chrome.runtime.sendMessage({
              type: "UNLOCK",
              masterPassword: pw,
            })) as BgResponse;
            if (res?.ok) {
              pwInput.value = "";
              await onUnlockSucceeded();
              return;
            }
            errorText = res?.error || "Mật khẩu chính không đúng.";
          } catch {
            errorText = "Không thể kết nối OpenVault.";
          }
          busy = false;
          submitBtn.disabled = false;
          submitBtn.textContent = "Mở khóa";
          errEl.textContent = errorText;
          errEl.style.display = "block";
          pwInput.select();
        })();
      });

      form.appendChild(inputWrap);
      form.appendChild(submitBtn);
      bodySlot.appendChild(form);
      bodySlot.appendChild(errEl);

      if (pinAvailable) {
        const footer = document.createElement("div");
        footer.className = "ov-unlock-footer";
        const switchBtn = document.createElement("button");
        switchBtn.type = "button";
        switchBtn.className = "ov-unlock-link";
        switchBtn.textContent = "Dùng mã PIN";
        switchBtn.addEventListener("click", () => {
          mode = "pin";
          errorText = "";
          renderBody(true);
        });
        footer.appendChild(switchBtn);
        bodySlot.appendChild(footer);
      }

      if (shouldFocus) {
        window.requestAnimationFrame(() => pwInput.focus());
      }
    }
  }

  renderBody(opts.autoFocusField);
  return box;
}

function buildInlineUnlockPanel(
  input: HTMLInputElement,
  ctx: AutofillContextResponse,
  autoFocusField: boolean,
): HTMLDivElement {
  return createUnlockWidget({
    hasAccount: ctx.hasAccount,
    email: ctx.email,
    pinAvailableNow: ctx.pinAvailableNow,
    pinLength: ctx.pinLength,
    autoFocusField,
    showHeader: true,
    onClose: () => hideDropdown(),
    onUnlocked: async () => {
      showToast("Đã mở khóa OpenVault");
      try {
        const refreshed = (await chrome.runtime.sendMessage({
          type: "GET_AUTOFILL_CONTEXT",
          url: window.location.href,
          fieldKind: ctx.fieldKind,
        })) as BgResponse<AutofillContextResponse>;
        if (refreshed?.ok && !refreshed.data.disabled) {
          if (input.maxLength !== 1) {
            showInlineTriggerForContext(input, refreshed.data);
          }
          showDropdownForContext(input, refreshed.data, false);
          return;
        }
      } catch {
        // Fallback
      }
      hideDropdown();
    },
  });
}

function hideTrigger(): void {
  if (!overlayRef) return;
  overlayRef.container.querySelector(".ov-inline-trigger")?.remove();
}

function showToast(message: string): void {
  const { container } = ensureOverlay();
  container.querySelector(".ov-toast")?.remove();

  const toast = document.createElement("div");
  toast.className = "ov-toast";
  toast.textContent = message;
  container.appendChild(toast);

  window.setTimeout(() => toast.remove(), 2900);
}

function positionTriggerForInput(trigger: HTMLButtonElement, input: HTMLInputElement): void {
  const rect = input.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) {
    trigger.style.display = "none";
    return;
  }
  trigger.style.display = "flex";
  trigger.style.top = `${Math.round(rect.top + (rect.height - 22) / 2)}px`;
  trigger.style.left = `${Math.round(rect.right - 28)}px`;
}

function createDropdownItem(
  avatarText: string,
  avatarClass: string,
  title: string,
  subtitle: string,
  badgeText?: string,
  codeLine?: string,
): HTMLButtonElement {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "ov-item";

  const avatar = document.createElement("div");
  avatar.className = `ov-avatar ${avatarClass}`.trim();
  avatar.textContent = avatarText;

  const meta = document.createElement("div");
  meta.className = "ov-meta";
  const nameEl = document.createElement("div");
  nameEl.className = "ov-name";
  nameEl.textContent = title;
  meta.appendChild(nameEl);

  if (codeLine) {
    const codeEl = document.createElement("div");
    codeEl.className = "ov-code";
    codeEl.textContent = codeLine;
    meta.appendChild(codeEl);
  } else {
    const subEl = document.createElement("div");
    subEl.className = "ov-sub";
    subEl.textContent = subtitle;
    meta.appendChild(subEl);
  }

  btn.appendChild(avatar);
  btn.appendChild(meta);

  if (badgeText) {
    const badge = document.createElement("span");
    badge.className = "ov-badge";
    badge.textContent = badgeText;
    btn.appendChild(badge);
  }

  return btn;
}

function appendLoginItems(
  list: HTMLDivElement,
  input: HTMLInputElement,
  matches: AutofillCandidate[],
): void {
  for (const item of matches) {
    const btn = createDropdownItem(
      (item.name.trim()[0] ?? "O").toUpperCase(),
      "",
      item.name,
      item.username || "(Không có username)",
      item.totp ? "2FA" : undefined,
    );

    btn.addEventListener("mousedown", (e) => {
      e.preventDefault();
      fillLoginFields(item.username, item.password, input);
      hideDropdown();

      void (async () => {
        try {
          const notify = (await chrome.runtime.sendMessage({
            type: "NOTIFY_LOGIN_FILLED",
            cipherId: item.id,
          })) as BgResponse<{ copiedTotp: string | null }>;
          const copied = notify?.ok ? notify.data.copiedTotp : null;
          if (copied) {
            showToast(`Đã điền ${item.name} · Đã sao chép mã 2FA (${formatTotpCode(copied)})`);
          } else {
            showToast(`Đã điền ${item.name}`);
          }
        } catch {
          showToast(`Đã điền ${item.name}`);
        }
      })();
    });

    list.appendChild(btn);
  }
}

function showDropdownForContext(
  input: HTMLInputElement,
  ctx: AutofillContextResponse,
  focusUnlockField = false,
): void {
  const rect = input.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) return;

  const { container } = ensureOverlay();
  hideDropdown();
  dropdownOpen = true;

  const dropdown = document.createElement("div");
  dropdown.className = "ov-dropdown";
  const estimatedHeight = ctx.unlocked ? 250 : 185;
  const fitsBelow = rect.bottom + 6 + estimatedHeight <= window.innerHeight;
  const top = fitsBelow
    ? rect.bottom + 5
    : Math.max(8, rect.top - estimatedHeight - 5);
  const dropWidth = Math.max(292, Math.min(350, rect.width));
  const left = Math.min(window.innerWidth - dropWidth - 8, Math.max(8, rect.left));
  dropdown.style.top = `${Math.round(top)}px`;
  dropdown.style.left = `${Math.round(left)}px`;
  dropdown.style.width = `${Math.round(dropWidth)}px`;

  const list = document.createElement("div");
  list.className = "ov-list";
  let itemCount = 0;

  // 1. New-password strong autosuggest (works even if vault is locked!)
  if (ctx.fieldKind === "new-password" && ctx.suggestedPassword) {
    let currentSuggested = ctx.suggestedPassword;
    const row = document.createElement("div");
    row.className = "ov-suggest-row";

    const suggestBtn = createDropdownItem(
      "✦",
      "ov-avatar-emerald",
      "Dùng mật khẩu mạnh đề xuất",
      "",
      "Mạnh",
      currentSuggested,
    );
    const codeDisplay = suggestBtn.querySelector<HTMLDivElement>(".ov-code");

    suggestBtn.addEventListener("mousedown", (e) => {
      e.preventDefault();
      fillNewPasswordFields(currentSuggested, input);
      hideDropdown();
      if (ctx.autoCopyGeneratedPassword) {
        void navigator.clipboard.writeText(currentSuggested).catch(() => undefined);
        void chrome.runtime
          .sendMessage({ type: "SCHEDULE_CLIPBOARD_CLEAR", copyText: currentSuggested })
          .catch(() => undefined);
        showToast("Đã điền & sao chép mật khẩu mạnh vào bảng tạm");
      } else {
        showToast("Đã điền mật khẩu mạnh vào biểu mẫu");
      }
    });

    const refreshBtn = document.createElement("button");
    refreshBtn.type = "button";
    refreshBtn.className = "ov-refresh-btn";
    refreshBtn.title = "Tạo mật khẩu khác";
    refreshBtn.textContent = "↻";
    refreshBtn.addEventListener("mousedown", (e) => {
      e.preventDefault();
      void (async () => {
        const res = (await chrome.runtime.sendMessage({
          type: "GENERATE_INLINE_PASSWORD",
        })) as BgResponse<{ password: string }>;
        if (res?.ok && res.data.password) {
          currentSuggested = res.data.password;
          if (codeDisplay) codeDisplay.textContent = currentSuggested;
        }
      })();
    });

    row.appendChild(suggestBtn);
    row.appendChild(refreshBtn);
    list.appendChild(row);
    itemCount++;

    if (!ctx.unlocked || ctx.matches.length > 0) {
      const divider = document.createElement("div");
      divider.className = "ov-divider";
      list.appendChild(divider);
    }
  }

  if (!ctx.unlocked) {
    if (itemCount > 0) {
      dropdown.appendChild(list);
    }
    dropdown.appendChild(buildInlineUnlockPanel(input, ctx, focusUnlockField));
    container.appendChild(dropdown);
    return;
  } else {
    // 2. Login matches (for login or change-password forms)
    if ((ctx.fieldKind === "login" || ctx.fieldKind === "new-password") && ctx.matches.length > 0) {
      appendLoginItems(list, input, ctx.matches);
      itemCount += ctx.matches.length;
    }

    // 3. 2FA / TOTP matches
    if (ctx.fieldKind === "totp" && ctx.totpMatches.length > 0) {
      const header = document.createElement("div");
      header.className = "ov-header";
      header.textContent = "Mã xác thực 2FA (TOTP)";
      list.appendChild(header);

      for (const totpItem of ctx.totpMatches) {
        const btn = createDropdownItem(
          "2F",
          "ov-avatar-emerald",
          totpItem.name,
          totpItem.username,
          `${totpItem.remainingSeconds}s`,
          formatTotpCode(totpItem.code),
        );
        btn.addEventListener("mousedown", (e) => {
          e.preventDefault();
          fillTotpField(totpItem.code, input);
          hideDropdown();
          showToast(`Đã điền mã 2FA (${formatTotpCode(totpItem.code)})`);
        });
        list.appendChild(btn);
        itemCount++;
      }
    }

    // 4. Credit cards
    if (ctx.fieldKind === "card" && ctx.cards.length > 0) {
      const header = document.createElement("div");
      header.className = "ov-header";
      header.textContent = "Thẻ thanh toán đã lưu";
      list.appendChild(header);

      for (const card of ctx.cards) {
        const exp = card.expMonth && card.expYear ? `${card.expMonth}/${card.expYear.slice(-2)}` : "";
        const sub = [card.brand, maskCardNumber(card.number), exp].filter(Boolean).join(" · ");
        const btn = createDropdownItem(
          "💳",
          "ov-avatar-amber",
          card.name,
          sub || card.cardholderName || "Thẻ tín dụng",
          card.brand || "Thẻ",
        );
        btn.addEventListener("mousedown", (e) => {
          e.preventDefault();
          fillCardFields(card, input);
          hideDropdown();
          showToast(`Đã điền thẻ ${card.name}`);
        });
        list.appendChild(btn);
        itemCount++;
      }
    }

    // 5. Identities
    if (ctx.fieldKind === "identity" && ctx.identities.length > 0) {
      const header = document.createElement("div");
      header.className = "ov-header";
      header.textContent = "Danh tính đã lưu";
      list.appendChild(header);

      for (const idn of ctx.identities) {
        const sub = [idn.fullName, idn.email || idn.phone].filter(Boolean).join(" · ");
        const btn = createDropdownItem(
          (idn.fullName[0] ?? "D").toUpperCase(),
          "ov-avatar-violet",
          idn.name,
          sub || idn.address1 || "Danh tính cá nhân",
          "Danh tính",
        );
        btn.addEventListener("mousedown", (e) => {
          e.preventDefault();
          fillIdentityFields(idn, input);
          hideDropdown();
          showToast(`Đã điền danh tính ${idn.name}`);
        });
        list.appendChild(btn);
        itemCount++;
      }
    }
  }

  if (itemCount === 0) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent =
      ctx.fieldKind === "totp"
        ? "Chưa có mã 2FA nào khớp với trang này."
        : ctx.fieldKind === "card"
          ? "Chưa có thẻ thanh toán nào trong OpenVault."
          : ctx.fieldKind === "identity"
            ? "Chưa có danh tính nào trong OpenVault."
            : "Chưa có tài khoản OpenVault nào khớp với trang này.";
    dropdown.appendChild(empty);
  } else {
    dropdown.appendChild(list);
  }

  container.appendChild(dropdown);
}

function showInlineTriggerForContext(
  input: HTMLInputElement,
  ctx: AutofillContextResponse,
): void {
  const { container } = ensureOverlay();
  hideTrigger();

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "ov-inline-trigger";
  const tipText =
    ctx.fieldKind === "totp"
      ? "Điền mã 2FA OpenVault"
      : ctx.fieldKind === "card"
        ? "Điền thẻ OpenVault"
        : ctx.fieldKind === "identity"
          ? "Điền danh tính OpenVault"
          : ctx.fieldKind === "new-password"
            ? "Tạo mật khẩu OpenVault"
            : "Dùng OpenVault";
  trigger.setAttribute("aria-label", tipText);

  const svgNS = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNS, "svg");
  svg.setAttribute("width", "12");
  svg.setAttribute("height", "12");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  const circle = document.createElementNS(svgNS, "circle");
  circle.setAttribute("cx", "12");
  circle.setAttribute("cy", "10");
  circle.setAttribute("r", "4");
  circle.setAttribute("fill", "#ffffff");
  const stem = document.createElementNS(svgNS, "path");
  stem.setAttribute("d", "M10 13h4l1 7H9l1-7z");
  stem.setAttribute("fill", "#ffffff");
  svg.appendChild(circle);
  svg.appendChild(stem);

  const tip = document.createElement("span");
  tip.className = "ov-tip";
  tip.textContent = tipText;

  trigger.appendChild(svg);
  trigger.appendChild(tip);
  positionTriggerForInput(trigger, input);

  trigger.addEventListener("mousedown", (e) => {
    e.preventDefault();
    interactingWithOverlay = true;
    if (dropdownOpen) {
      hideDropdown();
    } else {
      showDropdownForContext(input, ctx, !ctx.unlocked);
    }
  });

  container.appendChild(trigger);
}

function showSaveBanner(pending: PendingSaveCredential): void {
  const { container } = ensureOverlay();
  container.querySelector(".ov-banner")?.remove();

  const banner = document.createElement("div");
  banner.className = "ov-banner";

  const title = document.createElement("div");
  title.className = "ov-banner-title";
  const dot = document.createElement("span");
  dot.className = "ov-dot";
  const text = document.createElement("span");
  text.textContent = "Lưu mật khẩu vào OpenVault?";
  title.appendChild(dot);
  title.appendChild(text);

  const sub = document.createElement("div");
  sub.className = "ov-banner-sub";
  sub.textContent = `${pending.username || "(Chưa rõ tài khoản)"} · ${pending.origin}`;

  const actions = document.createElement("div");
  actions.className = "ov-actions";

  const dismissBtn = document.createElement("button");
  dismissBtn.type = "button";
  dismissBtn.className = "ov-btn ov-btn-ghost";
  dismissBtn.textContent = "Bỏ qua";
  dismissBtn.addEventListener("click", async () => {
    banner.remove();
    await chrome.runtime.sendMessage({ type: "DISMISS_PENDING_SAVE" }).catch(() => undefined);
  });

  const saveBtn = document.createElement("button");
  saveBtn.type = "button";
  saveBtn.className = "ov-btn ov-btn-primary";
  saveBtn.textContent = "Lưu vào Vault";
  saveBtn.addEventListener("click", async () => {
    saveBtn.disabled = true;
    saveBtn.textContent = "Đang mã hóa...";
    try {
      const res = (await chrome.runtime.sendMessage({
        type: "SAVE_PENDING_CREDENTIAL",
      })) as BgResponse;
      banner.remove();
      showToast(res?.ok ? "Đã mã hóa và lưu vào OpenVault" : "Không thể lưu tài khoản");
    } catch {
      banner.remove();
    }
  });

  actions.appendChild(dismissBtn);
  actions.appendChild(saveBtn);
  banner.appendChild(title);
  banner.appendChild(sub);
  banner.appendChild(actions);
  container.appendChild(banner);
}

// --- Input Binding & Submission Capture ---

async function handleInputFocus(input: HTMLInputElement): Promise<void> {
  const fieldKind = detectFieldKind(input);
  if (!fieldKind) return;

  activeInput = input;
  try {
    const res = (await chrome.runtime.sendMessage({
      type: "GET_AUTOFILL_CONTEXT",
      url: window.location.href,
      fieldKind,
    })) as BgResponse<AutofillContextResponse>;

    if (!res?.ok || res.data.disabled) return;
    const ctx = res.data;

    // For tiny split OTP inputs (maxLength === 1), open dropdown directly without covering the single digit box with a trigger circle
    if (input.maxLength !== 1) {
      showInlineTriggerForContext(input, ctx);
    }

    const hasSuggestions =
      ctx.matches.length > 0 ||
      ctx.totpMatches.length > 0 ||
      ctx.cards.length > 0 ||
      ctx.identities.length > 0 ||
      Boolean(ctx.suggestedPassword);

    if (hasSuggestions || (!ctx.unlocked && ctx.hasAccount)) {
      showDropdownForContext(input, ctx, false);
    }
  } catch {
    // Extension reloaded
  }
}

function bindCandidateInputs(): void {
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input"));
  const targets = inputs.filter(
    (el) => !el.hasAttribute(HOST_ATTR) && isVisibleInput(el) && detectFieldKind(el) !== null,
  );
  if (targets.length === 0) return;

  window.requestAnimationFrame(() => {
    for (const input of targets) {
      if (input.hasAttribute(HOST_ATTR)) continue;
      input.setAttribute(HOST_ATTR, "1");
      input.addEventListener("focus", () => {
        void handleInputFocus(input);
      });
      input.addEventListener("blur", () => {
        window.setTimeout(() => {
          const active = document.activeElement;
          const insideOverlay = interactingWithOverlay || (overlayRef !== null && active === overlayRef.host);
          if (active !== input && !insideOverlay) {
            hideDropdown();
            hideTrigger();
          }
        }, 180);
      });
    }
  });
}

function scheduleScan(): void {
  if (scanQueued) return;
  scanQueued = true;
  window.requestAnimationFrame(() => {
    scanQueued = false;
    bindCandidateInputs();
  });
}

function captureFormSubmission(form: HTMLFormElement): void {
  const inputs = Array.from(form.querySelectorAll<HTMLInputElement>("input")).filter(
    isVisibleInput,
  );
  const passwordInput = inputs.find((i) => i.type === "password" && i.value.length >= 4);
  if (!passwordInput) return;

  const { usernameInput } = findCompanionLoginFields(passwordInput);
  const username = usernameInput?.value.trim() ?? "";
  const password = passwordInput.value;

  void chrome.runtime
    .sendMessage({
      type: "CAPTURE_LOGIN_SUBMIT",
      payload: {
        url: window.location.href,
        origin: window.location.origin,
        title: document.title || window.location.hostname.replace(/^www\./, ""),
        username,
        password,
      },
    })
    .catch(() => undefined);
}

// --- WebAuthn / Passkeys Bridge (ISOLATED <-> MAIN) & 1Password Slide-3 Prompt ---

function postWebAuthnResponse(payload: SerializedWebAuthnResponse): void {
  const envelope: WebAuthnBridgeResponseEnvelope = {
    channel: WEBAUTHN_BRIDGE_CHANNEL,
    direction: "isolated-to-main",
    payload,
  };
  const targetOrigin =
    window.location.origin && window.location.origin !== "null" ? window.location.origin : "*";
  window.postMessage(envelope, targetOrigin);
}

function createPasskeyGlyphSvg(): SVGSVGElement {
  const svgNS = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNS, "svg");
  svg.setAttribute("width", "14");
  svg.setAttribute("height", "14");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "#ffffff");
  svg.setAttribute("stroke-width", "2.1");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");

  const head = document.createElementNS(svgNS, "circle");
  head.setAttribute("cx", "9");
  head.setAttribute("cy", "7");
  head.setAttribute("r", "3.5");
  const body = document.createElementNS(svgNS, "path");
  body.setAttribute("d", "M3 19c0-3 2.7-5 6-5s6 2 6 5");
  const keyRing = document.createElementNS(svgNS, "circle");
  keyRing.setAttribute("cx", "18");
  keyRing.setAttribute("cy", "11");
  keyRing.setAttribute("r", "2.5");
  const keyStem = document.createElementNS(svgNS, "path");
  keyStem.setAttribute("d", "M18 13.5V19l2-1.5");

  svg.appendChild(head);
  svg.appendChild(body);
  svg.appendChild(keyRing);
  svg.appendChild(keyStem);
  return svg;
}

function createHardwareKeySvg(): SVGSVGElement {
  const svgNS = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNS, "svg");
  svg.setAttribute("width", "15");
  svg.setAttribute("height", "15");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "2");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");

  const rect = document.createElementNS(svgNS, "rect");
  rect.setAttribute("x", "6");
  rect.setAttribute("y", "8");
  rect.setAttribute("width", "12");
  rect.setAttribute("height", "12");
  rect.setAttribute("rx", "2.5");
  const usb = document.createElementNS(svgNS, "path");
  usb.setAttribute("d", "M9 8V4h6v4");
  const dot = document.createElementNS(svgNS, "circle");
  dot.setAttribute("cx", "12");
  dot.setAttribute("cy", "14");
  dot.setAttribute("r", "1.5");

  svg.appendChild(rect);
  svg.appendChild(usb);
  svg.appendChild(dot);
  return svg;
}

function removePasskeyPrompt(): void {
  overlayRef?.container.querySelector(".ov-pk-card")?.remove();
}

function buildPasskeyCardShell(
  titleText: string,
  rpId: string,
  onFallback: () => void,
  onCancel: () => void,
): { card: HTMLDivElement; body: HTMLDivElement; errBox: HTMLDivElement } {
  const { container } = ensureOverlay();
  removePasskeyPrompt();

  const card = document.createElement("div");
  card.className = "ov-pk-card";
  card.setAttribute("role", "dialog");
  card.setAttribute("aria-label", titleText);

  const head = document.createElement("div");
  head.className = "ov-pk-head";

  const brand = document.createElement("div");
  brand.className = "ov-pk-brand";
  const logo = document.createElement("div");
  logo.className = "ov-pk-logo";
  logo.appendChild(createKeyholeSvg(13));
  const title = document.createElement("div");
  title.className = "ov-pk-title";
  title.textContent = titleText;
  brand.appendChild(logo);
  brand.appendChild(title);

  const actions = document.createElement("div");
  actions.className = "ov-pk-head-actions";

  const hwBtn = document.createElement("button");
  hwBtn.type = "button";
  hwBtn.className = "ov-pk-icon-btn";
  hwBtn.title = "Dùng trình duyệt / khóa bảo mật thiết bị";
  hwBtn.setAttribute("aria-label", "Dùng trình duyệt / thiết bị");
  hwBtn.appendChild(createHardwareKeySvg());
  hwBtn.addEventListener("click", onFallback);

  const closeBtn = document.createElement("button");
  closeBtn.type = "button";
  closeBtn.className = "ov-pk-icon-btn";
  closeBtn.title = "Hủy";
  closeBtn.setAttribute("aria-label", "Hủy");
  closeBtn.textContent = "×";
  closeBtn.addEventListener("click", onCancel);

  actions.appendChild(hwBtn);
  actions.appendChild(closeBtn);
  head.appendChild(brand);
  head.appendChild(actions);

  const body = document.createElement("div");
  body.style.cssText = "display:flex;flex-direction:column;gap:8px;";

  const errBox = document.createElement("div");
  errBox.className = "ov-pk-err";
  errBox.style.display = "none";

  const footer = document.createElement("div");
  footer.className = "ov-pk-footer";
  const domainEl = document.createElement("span");
  domainEl.className = "ov-pk-domain";
  domainEl.textContent = rpId;
  const fallbackLink = document.createElement("button");
  fallbackLink.type = "button";
  fallbackLink.className = "ov-pk-fallback-link";
  fallbackLink.textContent = "Dùng trình duyệt / thiết bị";
  fallbackLink.addEventListener("click", onFallback);
  footer.appendChild(domainEl);
  footer.appendChild(fallbackLink);

  card.appendChild(head);
  card.appendChild(body);
  card.appendChild(errBox);
  card.appendChild(footer);
  container.appendChild(card);

  return { card, body, errBox };
}

async function handleWebAuthnCreate(req: SerializedCreateRequest): Promise<void> {
  const prepareRes = (await chrome.runtime
    .sendMessage({
      type: "PASSKEY_PREPARE_CREATE",
      origin: req.origin,
      rpId: req.rpId,
      excludeCredentialIds: req.excludeCredentialIds,
    })
    .catch(() => null)) as BgResponse<PasskeyPrepareCreateResponse> | null;

  if (
    !prepareRes?.ok ||
    !prepareRes.data.enabled ||
    !prepareRes.data.validOrigin ||
    !prepareRes.data.hasAccount
  ) {
    postWebAuthnResponse({ requestId: req.requestId, action: "fallback" });
    return;
  }

  let settled = false;
  const finish = (response: SerializedWebAuthnResponse) => {
    if (settled) return;
    settled = true;
    removePasskeyPrompt();
    postWebAuthnResponse(response);
  };

  const renderState = (prep: PasskeyPrepareCreateResponse) => {
    const { body, errBox } = buildPasskeyCardShell(
      "Lưu mã khóa (Passkey)",
      req.rpId,
      () => finish({ requestId: req.requestId, action: "fallback" }),
      () => finish({ requestId: req.requestId, action: "cancel", reason: "Người dùng đã hủy" }),
    );

    if (!prep.unlocked) {
      const unlockWidget = createUnlockWidget({
        hasAccount: prep.hasAccount,
        email: prep.email,
        pinAvailableNow: prep.pinAvailableNow,
        pinLength: prep.pinLength,
        autoFocusField: true,
        showHeader: false,
        onUnlocked: async () => {
          showToast("Đã mở khóa OpenVault");
          const next = (await chrome.runtime
            .sendMessage({
              type: "PASSKEY_PREPARE_CREATE",
              origin: req.origin,
              rpId: req.rpId,
              excludeCredentialIds: req.excludeCredentialIds,
            })
            .catch(() => null)) as BgResponse<PasskeyPrepareCreateResponse> | null;
          if (!next?.ok || !next.data.unlocked) {
            finish({ requestId: req.requestId, action: "fallback" });
            return;
          }
          renderState(next.data);
        },
      });
      body.appendChild(unlockWidget);
      return;
    }

    if (prep.alreadyExcluded) {
      const warn = document.createElement("div");
      warn.className = "ov-pk-warn";
      warn.textContent =
        "Trang web báo đã có mã khóa tương tự trong OpenVault. Bạn vẫn có thể lưu mã khóa mới hoặc chuyển sang thiết bị khác.";
      body.appendChild(warn);
    }

    const row = document.createElement("div");
    row.className = "ov-pk-row";

    const siteIcon = document.createElement("div");
    siteIcon.className = "ov-pk-site-icon";
    siteIcon.textContent = (req.rpName.trim()[0] || req.rpId[0] || "P").toUpperCase();

    const meta = document.createElement("div");
    meta.className = "ov-pk-meta";
    const rpEl = document.createElement("div");
    rpEl.className = "ov-pk-rp";
    rpEl.textContent = req.rpName || req.rpId;
    const userEl = document.createElement("div");
    userEl.className = "ov-pk-user";
    userEl.textContent = req.userName || req.userDisplayName || req.rpId;
    meta.appendChild(rpEl);
    meta.appendChild(userEl);

    const saveBtn = document.createElement("button");
    saveBtn.type = "button";
    saveBtn.className = "ov-pk-btn";
    saveBtn.appendChild(createPasskeyGlyphSvg());
    const btnText = document.createElement("span");
    btnText.textContent = "Lưu mã khóa";
    saveBtn.appendChild(btnText);

    saveBtn.addEventListener("click", () => {
      saveBtn.disabled = true;
      btnText.textContent = "Đang tạo...";
      errBox.style.display = "none";

      void (async () => {
        const execRes = (await chrome.runtime
          .sendMessage({
            type: "PASSKEY_EXECUTE_CREATE",
            origin: req.origin,
            rpId: req.rpId,
            rpName: req.rpName,
            challengeB64Url: req.challengeB64Url,
            userIdB64Url: req.userIdB64Url,
            userName: req.userName,
            userDisplayName: req.userDisplayName,
            discoverable: req.discoverable,
          })
          .catch(() => null)) as BgResponse<SerializedCreateResponseData> | null;

        if (execRes?.ok) {
          showToast(`Đã lưu mã khóa cho ${req.rpName || req.rpId}`);
          finish({
            requestId: req.requestId,
            action: "ok",
            kind: "create",
            data: execRes.data,
          });
          return;
        }
        saveBtn.disabled = false;
        btnText.textContent = "Lưu mã khóa";
        errBox.textContent = execRes?.error || "Không thể tạo mã khóa Passkey.";
        errBox.style.display = "block";
      })();
    });

    row.appendChild(siteIcon);
    row.appendChild(meta);
    row.appendChild(saveBtn);
    body.appendChild(row);
  };

  renderState(prepareRes.data);
}

async function handleWebAuthnGet(req: SerializedGetRequest): Promise<void> {
  const prepareRes = (await chrome.runtime
    .sendMessage({
      type: "PASSKEY_PREPARE_GET",
      origin: req.origin,
      rpId: req.rpId,
      allowCredentialIds: req.allowCredentialIds,
    })
    .catch(() => null)) as BgResponse<PasskeyPrepareGetResponse> | null;

  if (
    !prepareRes?.ok ||
    !prepareRes.data.enabled ||
    !prepareRes.data.validOrigin ||
    !prepareRes.data.hasAccount
  ) {
    postWebAuthnResponse({ requestId: req.requestId, action: "fallback" });
    return;
  }

  // Zero interruption when vault is already unlocked and no passkey matches this RP ID
  if (prepareRes.data.unlocked && prepareRes.data.matches.length === 0) {
    postWebAuthnResponse({ requestId: req.requestId, action: "fallback" });
    return;
  }

  let settled = false;
  const finish = (response: SerializedWebAuthnResponse) => {
    if (settled) return;
    settled = true;
    removePasskeyPrompt();
    postWebAuthnResponse(response);
  };

  const renderState = (prep: PasskeyPrepareGetResponse) => {
    if (prep.unlocked && prep.matches.length === 0) {
      finish({ requestId: req.requestId, action: "fallback" });
      return;
    }

    const { body, errBox } = buildPasskeyCardShell(
      "Đăng nhập bằng mã khóa",
      req.rpId,
      () => finish({ requestId: req.requestId, action: "fallback" }),
      () => finish({ requestId: req.requestId, action: "cancel", reason: "Người dùng đã hủy" }),
    );

    if (!prep.unlocked) {
      const unlockWidget = createUnlockWidget({
        hasAccount: prep.hasAccount,
        email: prep.email,
        pinAvailableNow: prep.pinAvailableNow,
        pinLength: prep.pinLength,
        autoFocusField: true,
        showHeader: false,
        onUnlocked: async () => {
          showToast("Đã mở khóa OpenVault");
          const next = (await chrome.runtime
            .sendMessage({
              type: "PASSKEY_PREPARE_GET",
              origin: req.origin,
              rpId: req.rpId,
              allowCredentialIds: req.allowCredentialIds,
            })
            .catch(() => null)) as BgResponse<PasskeyPrepareGetResponse> | null;
          if (!next?.ok || !next.data.unlocked || next.data.matches.length === 0) {
            finish({ requestId: req.requestId, action: "fallback" });
            return;
          }
          renderState(next.data);
        },
      });
      body.appendChild(unlockWidget);
      return;
    }

    const list = document.createElement("div");
    list.className = "ov-pk-list";

    for (const match of prep.matches) {
      const row = document.createElement("div");
      row.className = "ov-pk-row";

      const siteIcon = document.createElement("div");
      siteIcon.className = "ov-pk-site-icon";
      siteIcon.textContent = (match.cipherName.trim()[0] || match.rpId[0] || "P").toUpperCase();

      const meta = document.createElement("div");
      meta.className = "ov-pk-meta";
      const rpEl = document.createElement("div");
      rpEl.className = "ov-pk-rp";
      rpEl.textContent = match.cipherName || match.rpName || match.rpId;
      const userEl = document.createElement("div");
      userEl.className = "ov-pk-user";
      userEl.textContent = match.userName || match.userDisplayName || match.rpId;
      meta.appendChild(rpEl);
      meta.appendChild(userEl);

      const signInBtn = document.createElement("button");
      signInBtn.type = "button";
      signInBtn.className = "ov-pk-btn";
      signInBtn.appendChild(createPasskeyGlyphSvg());
      const btnText = document.createElement("span");
      btnText.textContent = "Đăng nhập";
      signInBtn.appendChild(btnText);

      signInBtn.addEventListener("click", () => {
        signInBtn.disabled = true;
        btnText.textContent = "Đang ký...";
        errBox.style.display = "none";

        void (async () => {
          const execRes = (await chrome.runtime
            .sendMessage({
              type: "PASSKEY_EXECUTE_GET",
              origin: req.origin,
              rpId: req.rpId,
              challengeB64Url: req.challengeB64Url,
              cipherId: match.cipherId,
              credentialId: match.credentialId,
            })
            .catch(() => null)) as BgResponse<SerializedGetResponseData> | null;

          if (execRes?.ok) {
            showToast(`Đã xác thực Passkey cho ${match.cipherName || match.rpId}`);
            finish({
              requestId: req.requestId,
              action: "ok",
              kind: "get",
              data: execRes.data,
            });
            return;
          }
          signInBtn.disabled = false;
          btnText.textContent = "Đăng nhập";
          errBox.textContent = execRes?.error || "Không thể ký xác thực Passkey.";
          errBox.style.display = "block";
        })();
      });

      row.appendChild(siteIcon);
      row.appendChild(meta);
      row.appendChild(signInBtn);
      list.appendChild(row);
    }

    body.appendChild(list);
  };

  renderState(prepareRes.data);
}

// Register WebAuthn bridge synchronously at document_start before page scripts run
window.addEventListener("message", (event: MessageEvent) => {
  if (event.source !== window) return;
  const msg = event.data as Partial<WebAuthnBridgeRequestEnvelope> | null | undefined;
  if (
    !msg ||
    msg.channel !== WEBAUTHN_BRIDGE_CHANNEL ||
    msg.direction !== "main-to-isolated" ||
    !msg.payload
  ) {
    return;
  }
  const req = msg.payload as SerializedWebAuthnRequest;
  if (req.kind === "create") {
    void handleWebAuthnCreate(req);
  } else if (req.kind === "get") {
    void handleWebAuthnGet(req);
  }
});

// --- Initialization & Listeners ---

function initDomAutofillObservers(): void {
  bindCandidateInputs();
  if (document.documentElement) {
    const observer = new MutationObserver(() => {
      scheduleScan();
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", initDomAutofillObservers, { once: true });
} else {
  initDomAutofillObservers();
}

window.addEventListener(
  "scroll",
  () => {
    if (activeInput && overlayRef) {
      const trigger = overlayRef.container.querySelector<HTMLButtonElement>(".ov-inline-trigger");
      if (trigger) positionTriggerForInput(trigger, activeInput);
      if (dropdownOpen) hideDropdown();
    }
  },
  true,
);

document.addEventListener(
  "submit",
  (event) => {
    if (event.target instanceof HTMLFormElement) {
      captureFormSubmission(event.target);
    }
  },
  true,
);

document.addEventListener(
  "pointerdown",
  (event) => {
    if (!dropdownOpen) return;
    const target = event.target as Node | null;
    if (overlayRef && target === overlayRef.host) {
      interactingWithOverlay = true;
      return;
    }
    if (activeInput && target === activeInput) return;
    interactingWithOverlay = false;
    hideDropdown();
    if (document.activeElement !== activeInput) {
      hideTrigger();
    }
  },
  true,
);

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    hideDropdown();
  }
});

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  void (async () => {
    if (message?.type === "OV_FILL_CREDENTIALS") {
      const ok = fillLoginFields(message.username ?? "", message.password ?? "", activeInput);
      if (ok && message.itemName) {
        if (typeof message.copiedTotpCode === "string" && message.copiedTotpCode) {
          showToast(
            `Đã điền ${message.itemName} · Đã sao chép mã 2FA (${formatTotpCode(message.copiedTotpCode)})`,
          );
        } else {
          showToast(`Đã điền ${message.itemName}`);
        }
      }
      sendResponse({ ok });
      return;
    }

    if (message?.type === "OV_FILL_CARD" && message.card) {
      const card = message.card as CardAutofillCandidate;
      const ok = fillCardFields(card, activeInput);
      if (ok) showToast(`Đã điền thẻ ${card.name}`);
      sendResponse({ ok });
      return;
    }

    if (message?.type === "OV_FILL_IDENTITY" && message.identity) {
      const identity = message.identity as IdentityAutofillCandidate;
      const ok = fillIdentityFields(identity, activeInput);
      if (ok) showToast(`Đã điền danh tính ${identity.name}`);
      sendResponse({ ok });
      return;
    }

    if (message?.type === "OV_SHOW_SAVE_BANNER" && message.pending) {
      showSaveBanner(message.pending as PendingSaveCredential);
      sendResponse({ ok: true });
      return;
    }

    if (message?.type === "OV_SHOW_TOAST" && typeof message.message === "string") {
      showToast(message.message);
      sendResponse({ ok: true });
      return;
    }

    if (message?.type === "OV_COPY_AND_TOAST" && typeof message.text === "string") {
      try {
        await navigator.clipboard.writeText(message.text);
        showToast(message.message ?? "Đã sao chép");
      } catch {
        showToast("Không thể truy cập clipboard trên trang này");
      }
      sendResponse({ ok: true });
      return;
    }

    sendResponse({ ok: false });
  })();
  return true;
});
