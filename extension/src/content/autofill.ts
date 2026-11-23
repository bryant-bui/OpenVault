import type { AutofillCandidate, BgResponse, PendingSaveCredential } from "../shared/types.js";

/**
 * OpenVault Content Script (ISOLATED World) — 1Password Slide-3 Style
 * - Renders an embedded circular OpenVault keyhole trigger inside the right edge
 *   of focused login/password inputs, complete with dark tooltip ("Dùng OpenVault")
 * - Opens a crisp elevated white/dark dropdown directly beneath the field inside
 *   a CLOSED Shadow DOM (zero CSS bleed, unreachable by page scripts)
 * - Dispatches native HTMLInputElement setter + input/change events for React/Vue/Angular
 * - Captures login form submissions to offer saving new passwords cleanly
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

function isVisibleInput(el: HTMLInputElement): boolean {
  if (el.disabled || el.readOnly) return false;
  const rect = el.getBoundingClientRect();
  if (rect.width < 60 || rect.height < 18) return false;
  const style = window.getComputedStyle(el);
  return style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
}

function isPasswordField(el: HTMLInputElement): boolean {
  return el.type === "password";
}

function isUsernameField(el: HTMLInputElement): boolean {
  if (!["text", "email", "tel"].includes(el.type)) return false;
  const hint = `${el.name} ${el.id} ${el.autocomplete} ${el.placeholder} ${el.getAttribute("aria-label") ?? ""}`.toLowerCase();
  if (/(search|query|filter|captcha|otp|code|zip|postal|city|address)/.test(hint)) {
    return false;
  }
  return /(user|email|login|account|phone|member|uid|identifier|handle)/.test(hint) || el.type === "email";
}

function findCompanionFields(anchor: HTMLInputElement): {
  usernameInput: HTMLInputElement | null;
  passwordInput: HTMLInputElement | null;
} {
  const scope: ParentNode = anchor.form ?? document;
  const inputs = Array.from(scope.querySelectorAll<HTMLInputElement>("input")).filter(isVisibleInput);

  let passwordInput: HTMLInputElement | null = isPasswordField(anchor) ? anchor : null;
  let usernameInput: HTMLInputElement | null = !isPasswordField(anchor) ? anchor : null;

  if (!passwordInput) {
    passwordInput = inputs.find(isPasswordField) ?? null;
  }
  if (!usernameInput && passwordInput) {
    const pwIndex = inputs.indexOf(passwordInput);
    for (let i = pwIndex - 1; i >= 0; i--) {
      const candidate = inputs[i]!;
      if (isUsernameField(candidate) || candidate.type === "text" || candidate.type === "email") {
        usernameInput = candidate;
        break;
      }
    }
  }

  return { usernameInput, passwordInput };
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

function fillFields(username: string, password: string, anchor?: HTMLInputElement | null): boolean {
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input")).filter(isVisibleInput);
  const start = anchor ?? inputs.find(isPasswordField) ?? inputs.find(isUsernameField) ?? null;
  if (!start) return false;

  const { usernameInput, passwordInput } = findCompanionFields(start);
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

    /* Embedded 1Password-style circular keyhole button inside the right edge of focused input */
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

    /* Crisp Slide-3 White Floating Autofill Dropdown */
    .ov-dropdown {
      position: fixed;
      pointer-events: auto;
      min-width: 268px;
      max-width: 340px;
      background: #ffffff;
      color: #0a1a33;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      box-shadow: 0 12px 32px rgba(10, 26, 51, 0.16), 0 2px 6px rgba(10, 26, 51, 0.06);
      overflow: hidden;
      animation: ovPop 130ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ov-list { max-height: 224px; overflow-y: auto; padding: 5px; }
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
    .ov-meta { min-width: 0; flex: 1; }
    .ov-name { font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ov-sub { font-size: 11.5px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; margin-top: 1px; }
    .ov-badge {
      font-size: 10px;
      font-weight: 700;
      padding: 2px 6px;
      border-radius: 6px;
      background: #ebf4ff;
      color: #0572EC;
    }
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
      .ov-sub, .ov-empty { color: #94a3b8; }
      .ov-badge { background: #172a4a; color: #60a5fa; }
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

  window.setTimeout(() => toast.remove(), 2500);
}

function positionTriggerForInput(trigger: HTMLButtonElement, input: HTMLInputElement): void {
  const rect = input.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) {
    trigger.style.display = "none";
    return;
  }
  trigger.style.display = "flex";
  trigger.style.top = `${Math.round(rect.top + (rect.height - 22) / 2)}px`;
  trigger.style.left = `${Math.round(rect.right - 29)}px`;
}

function showDropdownForInput(
  input: HTMLInputElement,
  unlocked: boolean,
  matches: AutofillCandidate[],
): void {
  const rect = input.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) return;

  const { container } = ensureOverlay();
  hideDropdown();
  dropdownOpen = true;

  const dropdown = document.createElement("div");
  dropdown.className = "ov-dropdown";
  const top = Math.min(window.innerHeight - 236, rect.bottom + 5);
  const left = Math.min(window.innerWidth - 348, Math.max(8, rect.left));
  dropdown.style.top = `${Math.max(8, top)}px`;
  dropdown.style.left = `${left}px`;
  dropdown.style.width = `${Math.max(268, Math.min(340, rect.width))}px`;

  if (!unlocked) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent = "OpenVault đang khóa — nhấn biểu tượng OpenVault trên thanh công cụ để mở khóa.";
    dropdown.appendChild(empty);
  } else if (matches.length === 0) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent = "Chưa có tài khoản OpenVault nào khớp với trang này.";
    dropdown.appendChild(empty);
  } else {
    const list = document.createElement("div");
    list.className = "ov-list";
    for (const item of matches) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "ov-item";

      const avatar = document.createElement("div");
      avatar.className = "ov-avatar";
      avatar.textContent = (item.name.trim()[0] ?? "O").toUpperCase();

      const meta = document.createElement("div");
      meta.className = "ov-meta";
      const nameEl = document.createElement("div");
      nameEl.className = "ov-name";
      nameEl.textContent = item.name;
      const subEl = document.createElement("div");
      subEl.className = "ov-sub";
      subEl.textContent = item.username || "(Không có username)";
      meta.appendChild(nameEl);
      meta.appendChild(subEl);

      btn.appendChild(avatar);
      btn.appendChild(meta);

      if (item.totp) {
        const badge = document.createElement("span");
        badge.className = "ov-badge";
        badge.textContent = "2FA";
        btn.appendChild(badge);
      }

      btn.addEventListener("mousedown", (e) => {
        e.preventDefault();
        fillFields(item.username, item.password, input);
        hideDropdown();
        showToast(`Đã điền ${item.name}`);
      });

      list.appendChild(btn);
    }
    dropdown.appendChild(list);
  }

  container.appendChild(dropdown);
}

function showInlineTriggerForInput(
  input: HTMLInputElement,
  unlocked: boolean,
  matches: AutofillCandidate[],
): void {
  const { container } = ensureOverlay();
  hideTrigger();

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "ov-inline-trigger";
  trigger.setAttribute("aria-label", "Dùng OpenVault");

  // Crisp SVG keyhole icon (1Password Slide-3 style)
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
  tip.textContent = "Dùng OpenVault";

  trigger.appendChild(svg);
  trigger.appendChild(tip);
  positionTriggerForInput(trigger, input);

  trigger.addEventListener("mousedown", (e) => {
    e.preventDefault();
    if (dropdownOpen) {
      hideDropdown();
    } else {
      showDropdownForInput(input, unlocked, matches);
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
  activeInput = input;
  try {
    const res = (await chrome.runtime.sendMessage({
      type: "GET_MATCHES_FOR_URL",
      url: window.location.href,
    })) as BgResponse<{ unlocked: boolean; disabled: boolean; matches: AutofillCandidate[] }>;

    if (!res?.ok || res.data.disabled) return;

    showInlineTriggerForInput(input, res.data.unlocked, res.data.matches);
    if (res.data.unlocked && res.data.matches.length > 0) {
      showDropdownForInput(input, true, res.data.matches);
    }
  } catch {
    // Extension reloaded
  }
}

function bindCandidateInputs(): void {
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>("input"));
  const targets = inputs.filter(
    (el) => !el.hasAttribute(HOST_ATTR) && isVisibleInput(el) && (isPasswordField(el) || isUsernameField(el)),
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
  const inputs = Array.from(form.querySelectorAll<HTMLInputElement>("input")).filter(isVisibleInput);
  const passwordInput = inputs.find((i) => isPasswordField(i) && i.value.length >= 4);
  if (!passwordInput) return;

  const { usernameInput } = findCompanionFields(passwordInput);
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
      const ok = fillFields(message.username ?? "", message.password ?? "", activeInput);
      if (ok && message.itemName) {
        showToast(`Đã điền ${message.itemName}`);
      }
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
