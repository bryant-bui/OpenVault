export const DEFAULT_SERVER_URL = "https://your-openvault-api.workers.dev";

export const LOCAL_KEYS = {
  ACCOUNT: "ov_account",
  VAULT_CACHE: "ov_vault_cache",
  SETTINGS: "ov_settings",
  DEVICE_ID: "ov_device_id",
  PIN_LOCAL: "ov_pin_local",
} as const;

export const SESSION_KEYS = {
  SECRETS: "ov_session_secrets",
  PENDING_SAVE: "ov_pending_save",
  PIN_SESSION: "ov_pin_session",
  BASIC_AUTH_ATTEMPTS: "ov_basic_auth_attempts",
} as const;

export const ALARMS = {
  AUTO_LOCK: "ov_auto_lock",
  SYNC_POLL: "ov_sync_poll",
  CLEAR_CLIPBOARD: "ov_clear_clipboard",
} as const;

export const CONTEXT_MENUS = {
  OPEN_SIDE_PANEL: "ov_open_side_panel",
  AUTOFILL_LOGIN: "ov_autofill_login",
  GENERATE_PASSWORD: "ov_generate_password",
} as const;

export const DEFAULT_SETTINGS = {
  autoLockMinutes: 15,
  clipboardClearSeconds: 30,
  pinMaxAttempts: 5,
  showInlineAutofill: true,
  autofillIdentity: true,
  autofillTotp: true,
  autoCopyTotp: true,
  autofillCard: true,
  autofillBasicAuth: true,
  autosuggestPassword: true,
  autoCopyGeneratedPassword: false,
  defaultBrowserPasswordManager: false,
  promptSaveCredentials: true,
  showFavicons: true,
} as const;
