import {
  classifyField,
  formatTotpCode,
  maskCardNumber,
  type FieldDescriptor,
} from "../shared/field-classifier.js";
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

    @media (prefers-color-scheme: dark) {
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
    }

    @keyframes ovPop {
      from { opacity: 0; transform: translateY(-3px) scale(0.985); }
      to { opacity: 1; transform: translateY(0) scale(1); }
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
  if (!overlayRef) return;
  overlayRef.container.querySelector(".ov-dropdown")?.remove();
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

function showDropdownForContext(input: HTMLInputElement, ctx: AutofillContextResponse): void {
  const rect = input.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) return;

  const { container } = ensureOverlay();
  hideDropdown();
  dropdownOpen = true;

  const dropdown = document.createElement("div");
  dropdown.className = "ov-dropdown";
  const top = Math.min(window.innerHeight - 260, rect.bottom + 5);
  const left = Math.min(window.innerWidth - 356, Math.max(8, rect.left));
  dropdown.style.top = `${Math.max(8, top)}px`;
  dropdown.style.left = `${left}px`;
  dropdown.style.width = `${Math.max(278, Math.min(350, rect.width))}px`;

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

    if (ctx.unlocked && ctx.matches.length > 0) {
      const divider = document.createElement("div");
      divider.className = "ov-divider";
      list.appendChild(divider);
    }
  }

  if (!ctx.unlocked) {
    if (itemCount === 0) {
      const empty = document.createElement("div");
      empty.className = "ov-empty";
      empty.textContent =
        "OpenVault đang khóa — nhấn biểu tượng OpenVault trên thanh công cụ để mở khóa.";
      dropdown.appendChild(empty);
      container.appendChild(dropdown);
      return;
    }
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
    if (dropdownOpen) {
      hideDropdown();
    } else {
      showDropdownForContext(input, ctx);
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

    if (hasSuggestions) {
      showDropdownForContext(input, ctx);
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
          if (document.activeElement !== input) {
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

// --- Initialization & Listeners ---

bindCandidateInputs();

const observer = new MutationObserver(() => {
  scheduleScan();
});
observer.observe(document.documentElement, { childList: true, subtree: true });

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
