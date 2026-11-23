import type {
  CipherPayload,
  FolderPayload,
  KdfConfig,
  PinProtectedRecord,
} from "@openvault/crypto-core";

export interface StoredPinState {
  /** Persisted record when requireMasterOnRestart === false; null when restart requires Master Password */
  record: PinProtectedRecord | null;
  /**
   * When requireMasterOnRestart === true, the PinProtectedRecord is encrypted with the 512-bit UserKey
   * so it cannot be brute-forced offline on disk, yet automatically restores into RAM after Master Password unlock.
   */
  encryptedSessionRecord?: string | null;
  requireMasterOnRestart: boolean;
  failedAttempts: number;
  maxAttempts: number;
}

export interface ExtensionSettings {
  // Security & Lock
  autoLockMinutes: number; // 0 = until Chrome closes
  clipboardClearSeconds: number; // 0 = off, 15, 30, 60, 120
  pinMaxAttempts: number; // 3 or 5

  // Autofill (Granular controls)
  showInlineAutofill: boolean;
  autofillIdentity: boolean;
  autofillTotp: boolean;
  autoCopyTotp: boolean;
  autofillCard: boolean;
  autofillBasicAuth: boolean;

  // Autosuggest
  autosuggestPassword: boolean;
  autoCopyGeneratedPassword: boolean;

  // Browser integration
  defaultBrowserPasswordManager: boolean;

  // General UX
  promptSaveCredentials: boolean;
  showFavicons: boolean;
}

export interface StoredAccount {
  serverUrl: string;
  email: string;
  name: string | null;
  kdfConfig: KdfConfig;
  protectedKey: string;
  encryptedPrivateKey: string | null;
  refreshToken: string;
  lastRevisionMs: number;
  lastSyncAt: string | null;
}

export interface SessionSecrets {
  /** Base64-encoded 64-byte UserSymmetricKey (stored strictly in chrome.storage.session RAM) */
  userKeyB64: string;
  accessToken: string;
  accessTokenExpiresAtMs: number;
  unlockedAtMs: number;
}

export interface StoredVaultCache {
  folders: FolderPayload[];
  ciphers: CipherPayload[];
  updatedAt: string;
}

export interface DecryptedVault {
  folders: FolderPayload[];
  ciphers: CipherPayload[];
  lastSyncAt: string | null;
}

export type InlineFieldKind = "login" | "new-password" | "totp" | "card" | "identity";

export interface AutofillCandidate {
  id: string;
  name: string;
  username: string;
  password: string;
  totp: string | null;
  uri: string | null;
  score: number;
}

export interface TotpAutofillCandidate {
  id: string;
  name: string;
  username: string;
  code: string;
  remainingSeconds: number;
  period: number;
}

export interface CardAutofillCandidate {
  id: string;
  name: string;
  cardholderName: string;
  brand: string;
  number: string;
  expMonth: string;
  expYear: string;
  code: string;
}

export interface IdentityAutofillCandidate {
  id: string;
  name: string;
  firstName: string;
  middleName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string;
  company: string;
  address1: string;
  address2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export interface AutofillContextResponse {
  unlocked: boolean;
  disabled: boolean;
  fieldKind: InlineFieldKind;
  matches: AutofillCandidate[];
  totpMatches: TotpAutofillCandidate[];
  cards: CardAutofillCandidate[];
  identities: IdentityAutofillCandidate[];
  suggestedPassword: string | null;
  autoCopyGeneratedPassword: boolean;
  autoCopyTotp: boolean;
}

export interface PendingSaveCredential {
  url: string;
  origin: string;
  title: string;
  username: string;
  password: string;
  detectedAt: number;
}

export interface R2BackupItem {
  key: string;
  size: number;
  uploaded: string;
  sha256?: string;
  trigger?: string;
  cipherCount?: string;
}

export interface VaultStatusResponse {
  hasAccount: boolean;
  unlocked: boolean;
  email: string | null;
  serverUrl: string;
  lastSyncAt: string | null;
  settings: ExtensionSettings;
  pendingSave: PendingSaveCredential | null;
  pinEnabled: boolean;
  pinAvailableNow: boolean;
  pinRequireMasterOnRestart: boolean;
  pinRemainingAttempts: number;
  pinMaxAttempts: number;
}

export type BgRequest =
  | { type: "GET_STATUS" }
  | {
      type: "REGISTER";
      serverUrl: string;
      email: string;
      name: string;
      masterPassword: string;
      masterPasswordHint?: string;
    }
  | {
      type: "LOGIN";
      serverUrl: string;
      email: string;
      masterPassword: string;
    }
  | {
      type: "UNLOCK";
      masterPassword: string;
    }
  | {
      type: "UNLOCK_WITH_PIN";
      pin: string;
    }
  | {
      type: "SET_PIN";
      pin: string;
      requireMasterOnRestart: boolean;
      maxAttempts?: number;
    }
  | { type: "DISABLE_PIN" }
  | { type: "LOCK" }
  | { type: "LOGOUT"; allDevices?: boolean }
  | { type: "GET_VAULT"; forceSync?: boolean }
  | { type: "SAVE_CIPHER"; cipher: CipherPayload }
  | { type: "DELETE_CIPHER"; id: string; permanent?: boolean }
  | { type: "RESTORE_CIPHER"; id: string }
  | { type: "SAVE_FOLDER"; folder: { id?: string; name: string } }
  | { type: "DELETE_FOLDER"; id: string }
  | { type: "IMPORT_BITWARDEN_JSON"; rawJson: string }
  | { type: "EXPORT_ENCRYPTED_BACKUP" }
  | { type: "TRIGGER_R2_BACKUP" }
  | { type: "LIST_R2_BACKUPS" }
  | { type: "UPDATE_SETTINGS"; settings: Partial<ExtensionSettings> }
  | { type: "GET_MATCHES_FOR_URL"; url: string }
  | { type: "GET_AUTOFILL_CONTEXT"; url: string; fieldKind: InlineFieldKind }
  | { type: "GENERATE_INLINE_PASSWORD" }
  | { type: "NOTIFY_LOGIN_FILLED"; cipherId: string }
  | { type: "SCHEDULE_CLIPBOARD_CLEAR"; copyText?: string }
  | { type: "AUTOFILL_IN_TAB"; tabId: number; cipherId?: string }
  | { type: "CAPTURE_LOGIN_SUBMIT"; payload: Omit<PendingSaveCredential, "detectedAt"> }
  | { type: "DISMISS_PENDING_SAVE" }
  | { type: "SAVE_PENDING_CREDENTIAL" }
  | { type: "TOUCH_ACTIVITY" };

export type BgResponse<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; error: string };
