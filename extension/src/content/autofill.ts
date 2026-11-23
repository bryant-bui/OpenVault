import type { AutofillCandidate, BgResponse, PendingSaveCredential } from "../shared/types.js";

/**
 * OpenVault Content Script (ISOLATED World)
 * - Detects login / password forms cleanly across traditional pages and SPAs
 * - Renders a sleek 1Password-style inline dropdown inside a CLOSED Shadow DOM
 *   (zero CSS bleed, unreachable by page scripts)
 * - Dispatches native input/change events so React/Vue/Angular forms pick up filled values
 * - Captures login submissions to offer saving new passwords via a non-intrusive top-right banner
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
let lastMatches: AutofillCandidate[] = [];
let scanQueued = false;

function isVisibleInput(el: HTMLInputElement): boolean {
  if (el.disabled || el.readOnly) return false;
  const rect = el.getBoundingClientRect();
  if (rect.width < 40 || rect.height < 16) return false;
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

/**
 * Sets input value using the native HTMLInputElement prototype setter so React/Vue/Angular
 * controlled inputs detect the change immediately.
 */
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

// --- Closed Shadow DOM UI ---

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
    :host { all: initial; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Inter, Roboto, sans-serif; }
    * { box-sizing: border-box; }
    .ov-dropdown {
      position: fixed;
      pointer-events: auto;
      min-width: 268px;
      max-width: 340px;
      background: #141923;
      color: #f1f5f9;
      border: 1px solid rgba(148, 163, 184, 0.22);
      border-radius: 14px;
      box-shadow: 0 18px 42px rgba(0, 0, 0, 0.45), 0 0 0 1px rgba(5, 114, 236, 0.2);
      overflow: hidden;
      animation: ovPop 140ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    @media (prefers-color-scheme: light) {
      .ov-dropdown {
        background: #ffffff;
        color: #0f172a;
        border: 1px solid #e2e8f0;
        box-shadow: 0 16px 36px rgba(15, 23, 42, 0.16);
      }
      .ov-header { border-bottom-color: #eef2f7 !important; color: #64748b !important; }
      .ov-item:hover { background: #f1f5f9 !important; }
      .ov-sub { color: #64748b !important; }
      .ov-banner {
        background: #ffffff !important;
        color: #0f172a !important;
        border-color: #dbe4f0 !important;
        box-shadow: 0 18px 40px rgba(15, 23, 42, 0.18) !important;
      }
      .ov-btn-ghost { color: #475569 !important; background: #f1f5f9 !important; }
    }
    .ov-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      font-size: 11px;
      font-weight: 600;
      letter-spacing: 0.03em;
      color: #94a3b8;
      border-bottom: 1px solid rgba(148, 163, 184, 0.14);
    }
    .ov-brand {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
    .ov-dot {
      width: 8px;
      height: 8px;
      border-radius: 999px;
      background: #0572EC;
      box-shadow: 0 0 0 3px rgba(5, 114, 236, 0.25);
    }
    .ov-list { max-height: 220px; overflow-y: auto; padding: 4px; }
    .ov-item {
      width: 100%;
      border: 0;
      background: transparent;
      color: inherit;
      text-align: left;
      padding: 8px 10px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      gap: 10px;
      cursor: pointer;
      transition: background 120ms ease;
    }
    .ov-item:hover { background: rgba(59, 139, 255, 0.14); }
    .ov-avatar {
      width: 28px;
      height: 28px;
      border-radius: 8px;
      background: linear-gradient(135deg, #0A84FF, #0455BF);
      color: #fff;
      font-weight: 700;
      font-size: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .ov-meta { min-width: 0; flex: 1; }
    .ov-name { font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ov-sub { font-size: 11.5px; color: #94a3b8; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .ov-badge {
      font-size: 10px;
      font-weight: 600;
      padding: 2px 6px;
      border-radius: 999px;
      background: rgba(5, 114, 236, 0.18);
      color: #3B8BFF;
    }
    .ov-empty { padding: 12px; font-size: 12px; color: #94a3b8; text-align: center; }
    .ov-banner {
      position: fixed;
      top: 16px;
      right: 16px;
      width: 332px;
      pointer-events: auto;
      background: #141923;
      color: #f1f5f9;
      border: 1px solid rgba(59, 139, 255, 0.35);
      border-radius: 16px;
      padding: 14px 16px;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
      animation: ovPop 180ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .ov-banner-title { font-size: 13.5px; font-weight: 700; margin-bottom: 4px; display: flex; align-items: center; gap: 8px; }
    .ov-banner-sub { font-size: 12px; color: #94a3b8; margin-bottom: 12px; word-break: break-all; }
    .ov-actions { display: flex; gap: 8px; justify-content: flex-end; }
    .ov-btn {
      border: 0;
      border-radius: 9px;
      padding: 7px 13px;
      font-size: 12px;
      font-weight: 600;
      cursor: pointer;
    }
    .ov-btn-primary { background: #0572EC; color: #ffffff; }
    .ov-btn-primary:hover { background: #0460C9; }
    .ov-btn-ghost { background: rgba(148, 163, 184, 0.16); color: #cbd5e1; }
    .ov-toast {
      position: fixed;
      bottom: 20px;
      right: 20px;
      pointer-events: auto;
      background: #0f172a;
      color: #f8fafc;
      border: 1px solid rgba(59, 139, 255, 0.35);
      padding: 10px 14px;
      border-radius: 12px;
      font-size: 12.5px;
      font-weight: 500;
      box-shadow: 0 14px 30px rgba(0, 0, 0, 0.4);
      animation: ovPop 140ms ease;
    }
    @keyframes ovPop {
      from { opacity: 0; transform: translateY(-4px) scale(0.98); }
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
  if (!overlayRef) return;
  const dd = overlayRef.container.querySelector(".ov-dropdown");
  if (dd) dd.remove();
}

function showToast(message: string): void {
  const { container } = ensureOverlay();
  const prev = container.querySelector(".ov-toast");
  if (prev) prev.remove();

  const toast = document.createElement("div");
  toast.className = "ov-toast";
  toast.textContent = message;
  container.appendChild(toast);

  window.setTimeout(() => {
    toast.remove();
  }, 2600);
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

  const dropdown = document.createElement("div");
  dropdown.className = "ov-dropdown";
  const top = Math.min(window.innerHeight - 240, rect.bottom + 6);
  const left = Math.min(window.innerWidth - 348, Math.max(8, rect.left));
  dropdown.style.top = `${Math.max(8, top)}px`;
  dropdown.style.left = `${left}px`;
  dropdown.style.width = `${Math.max(268, Math.min(340, rect.width))}px`;

  const header = document.createElement("div");
  header.className = "ov-header";
  const brand = document.createElement("span");
  brand.className = "ov-brand";
  const dot = document.createElement("span");
  dot.className = "ov-dot";
  const label = document.createElement("span");
  label.textContent = "OPENVAULT";
  brand.appendChild(dot);
  brand.appendChild(label);
  header.appendChild(brand);
  dropdown.appendChild(header);

  if (!unlocked) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent = "Vault đang khóa — bấm biểu tượng OpenVault trên thanh công cụ để mở khóa.";
    dropdown.appendChild(empty);
  } else if (matches.length === 0) {
    const empty = document.createElement("div");
    empty.className = "ov-empty";
    empty.textContent = "Chưa có tài khoản đã lưu cho trang này.";
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
      subEl.textContent = item.username || "(Không có tên đăng nhập)";
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

function showSaveBanner(pending: PendingSaveCredential): void {
  const { container } = ensureOverlay();
  const prev = container.querySelector(".ov-banner");
  if (prev) prev.remove();

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
    lastMatches = res.data.matches;

    // Only auto-open dropdown if there are matching accounts (avoid visual noise on unrelated inputs)
    if (res.data.unlocked && lastMatches.length > 0) {
      showDropdownForInput(input, true, lastMatches);
    }
  } catch {
    // Extension context invalidated on reload
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
          }
        }, 160);
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
