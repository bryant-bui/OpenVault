import type { CipherPayload, FolderPayload, KdfConfig } from "@openvault/crypto-core";

export interface ExtensionSettings {
  autoLockMinutes: number; // 0 = never until browser closes
  clipboardClearSeconds: number;
  showInlineAutofill: boolean;
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

export interface AutofillCandidate {
  id: string;
  name: string;
  username: string;
  password: string;
  totp: string | null;
  uri: string | null;
  score: number;
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
  | { type: "LOCK" }
  | { type: "LOGOUT"; allDevices?: boolean }
  | { type: "GET_VAULT"; forceSync?: boolean }
  | { type: "SAVE_CIPHER"; cipher: CipherPayload }
  | { type: "DELETE_CIPHER"; id: string; permanent?: boolean }
  | { type: "RESTORE_CIPHER"; id: string }
  | { type: "SAVE_FOLDER"; folder: FolderPayload }
  | { type: "DELETE_FOLDER"; id: string }
  | { type: "IMPORT_BITWARDEN_JSON"; rawJson: string }
  | { type: "EXPORT_ENCRYPTED_BACKUP" }
  | { type: "TRIGGER_R2_BACKUP" }
  | { type: "LIST_R2_BACKUPS" }
  | { type: "UPDATE_SETTINGS"; settings: Partial<ExtensionSettings> }
  | { type: "GET_MATCHES_FOR_URL"; url: string }
  | { type: "AUTOFILL_IN_TAB"; tabId: number; cipherId: string }
  | { type: "CAPTURE_LOGIN_SUBMIT"; payload: Omit<PendingSaveCredential, "detectedAt"> }
  | { type: "DISMISS_PENDING_SAVE" }
  | { type: "SAVE_PENDING_CREDENTIAL" }
  | { type: "TOUCH_ACTIVITY" };

export type BgResponse<T = unknown> =
  | { ok: true; data: T }
  | { ok: false; error: string };
