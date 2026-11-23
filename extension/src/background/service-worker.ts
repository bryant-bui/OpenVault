import {
  CipherType,
  DEFAULT_ARGON2ID_CONFIG,
  base64ToBytes,
  bytesToBase64,
  decryptCipher,
  decryptFolder,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptCipher,
  encryptString,
  generatePassword,
  generateProtectedRsaKeyPair,
  generateUserSymmetricKey,
  prepareBitwardenImport,
  protectSymmetricKey,
  splitSymmetricKey,
  stretchMasterKey,
  unprotectSymmetricKey,
  type BitwardenExportJson,
  type CipherPayload,
  type SymmetricKey,
} from "@openvault/crypto-core";
import {
  ApiError,
  apiCreateCipher,
  apiCreateFolder,
  apiDeleteFolder,
  apiGetRevisionDate,
  apiHardDeleteCipher,
  apiImportVault,
  apiListR2Backups,
  apiLoginPassword,
  apiLogout,
  apiPrelogin,
  apiRefreshToken,
  apiRegister,
  apiRestoreCipher,
  apiSoftDeleteCipher,
  apiSync,
  apiTriggerR2Backup,
  apiUpdateCipher,
  apiUpdateFolder,
  normalizeServerUrl,
} from "../shared/api.js";
import {
  ALARMS,
  CONTEXT_MENUS,
  DEFAULT_SERVER_URL,
  DEFAULT_SETTINGS,
  LOCAL_KEYS,
  SESSION_KEYS,
} from "../shared/constants.js";
import { findMatchingLogins } from "../shared/domain-match.js";
import type {
  BgRequest,
  BgResponse,
  DecryptedVault,
  ExtensionSettings,
  PendingSaveCredential,
  SessionSecrets,
  StoredAccount,
  StoredVaultCache,
  VaultStatusResponse,
} from "../shared/types.js";

// --- Storage Helpers (Zero mutable global state in MV3 Service Worker) ---

async function ensureSessionAccessPolicy(): Promise<void> {
  try {
    await chrome.storage.session.setAccessLevel({ accessLevel: "TRUSTED_CONTEXTS" });
  } catch {
    // Ignore on older Chromium builds
  }
}

async function getSettings(): Promise<ExtensionSettings> {
  const res = await chrome.storage.local.get(LOCAL_KEYS.SETTINGS);
  return { ...DEFAULT_SETTINGS, ...(res[LOCAL_KEYS.SETTINGS] as Partial<ExtensionSettings> | undefined) };
}

async function getAccount(): Promise<StoredAccount | null> {
  const res = await chrome.storage.local.get(LOCAL_KEYS.ACCOUNT);
  return (res[LOCAL_KEYS.ACCOUNT] as StoredAccount | undefined) ?? null;
}

async function setAccount(account: StoredAccount | null): Promise<void> {
  if (!account) {
    await chrome.storage.local.remove(LOCAL_KEYS.ACCOUNT);
    return;
  }
  await chrome.storage.local.set({ [LOCAL_KEYS.ACCOUNT]: account });
}

async function getSessionSecrets(): Promise<SessionSecrets | null> {
  const res = await chrome.storage.session.get(SESSION_KEYS.SECRETS);
  return (res[SESSION_KEYS.SECRETS] as SessionSecrets | undefined) ?? null;
}

async function setSessionSecrets(secrets: SessionSecrets | null): Promise<void> {
  if (!secrets) {
    await chrome.storage.session.remove(SESSION_KEYS.SECRETS);
    return;
  }
  await chrome.storage.session.set({ [SESSION_KEYS.SECRETS]: secrets });
}

async function getVaultCache(): Promise<StoredVaultCache | null> {
  const res = await chrome.storage.local.get(LOCAL_KEYS.VAULT_CACHE);
  return (res[LOCAL_KEYS.VAULT_CACHE] as StoredVaultCache | undefined) ?? null;
}

async function setVaultCache(cache: StoredVaultCache | null): Promise<void> {
  if (!cache) {
    await chrome.storage.local.remove(LOCAL_KEYS.VAULT_CACHE);
    return;
  }
  await chrome.storage.local.set({ [LOCAL_KEYS.VAULT_CACHE]: cache });
}

async function getDeviceId(): Promise<string> {
  const res = await chrome.storage.local.get(LOCAL_KEYS.DEVICE_ID);
  const existing = res[LOCAL_KEYS.DEVICE_ID] as string | undefined;
  if (existing) return existing;
  const fresh = crypto.randomUUID();
  await chrome.storage.local.set({ [LOCAL_KEYS.DEVICE_ID]: fresh });
  return fresh;
}

async function getPendingSave(): Promise<PendingSaveCredential | null> {
  const res = await chrome.storage.session.get(SESSION_KEYS.PENDING_SAVE);
  return (res[SESSION_KEYS.PENDING_SAVE] as PendingSaveCredential | undefined) ?? null;
}

async function scheduleAutoLock(): Promise<void> {
  const settings = await getSettings();
  await chrome.alarms.clear(ALARMS.AUTO_LOCK);
  if (settings.autoLockMinutes > 0) {
    await chrome.alarms.create(ALARMS.AUTO_LOCK, {
      delayInMinutes: Math.max(0.5, settings.autoLockMinutes),
    });
  }
}

async function getUnlockedKey(): Promise<{ key: SymmetricKey; secrets: SessionSecrets; account: StoredAccount }> {
  const [account, secrets] = await Promise.all([getAccount(), getSessionSecrets()]);
  if (!account || !secrets) {
    throw new Error("Vault đang khóa. Vui lòng mở khóa trước.");
  }
  await scheduleAutoLock();
  return {
    key: splitSymmetricKey(base64ToBytes(secrets.userKeyB64)),
    secrets,
    account,
  };
}

/**
 * Ensures a valid JWT access token, automatically rotating the refresh token if expired or near expiry.
 */
async function withValidAccessToken<T>(
  operation: (serverUrl: string, accessToken: string) => Promise<T>,
): Promise<T> {
  const [account, secrets] = await Promise.all([getAccount(), getSessionSecrets()]);
  if (!account || !secrets) {
    throw new Error("Vault đang khóa.");
  }

  let currentToken = secrets.accessToken;
  const needsRefresh = Date.now() >= secrets.accessTokenExpiresAtMs - 60_000;

  if (needsRefresh && account.refreshToken) {
    const refreshed = await apiRefreshToken(account.serverUrl, account.refreshToken);
    currentToken = refreshed.access_token;
    await setAccount({ ...account, refreshToken: refreshed.refresh_token });
    await setSessionSecrets({
      ...secrets,
      accessToken: refreshed.access_token,
      accessTokenExpiresAtMs: Date.now() + refreshed.expires_in * 1000,
    });
  }

  try {
    return await operation(account.serverUrl, currentToken);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401 && account.refreshToken) {
      const refreshed = await apiRefreshToken(account.serverUrl, account.refreshToken);
      await setAccount({ ...account, refreshToken: refreshed.refresh_token });
      await setSessionSecrets({
        ...secrets,
        accessToken: refreshed.access_token,
        accessTokenExpiresAtMs: Date.now() + refreshed.expires_in * 1000,
      });
      return operation(account.serverUrl, refreshed.access_token);
    }
    throw err;
  }
}

async function syncEncryptedVaultFromServer(force = false): Promise<StoredVaultCache> {
  const account = await getAccount();
  if (!account) throw new Error("Chưa đăng nhập tài khoản.");

  const cached = await getVaultCache();
  if (!force && cached) {
    try {
      const remoteRevision = await withValidAccessToken((url, token) =>
        apiGetRevisionDate(url, token),
      );
      if (remoteRevision <= account.lastRevisionMs) {
        return cached;
      }
    } catch {
      // Offline fallback: return cached encrypted vault
      return cached;
    }
  }

  const syncData = await withValidAccessToken((url, token) => apiSync(url, token));
  const nowIso = new Date().toISOString();
  const nextCache: StoredVaultCache = {
    folders: syncData.folders,
    ciphers: syncData.ciphers,
    updatedAt: nowIso,
  };
  await setVaultCache(nextCache);
  await setAccount({
    ...account,
    name: syncData.profile.name,
    protectedKey: syncData.profile.key,
    encryptedPrivateKey: syncData.profile.privateKey,
    lastRevisionMs: Date.now(),
    lastSyncAt: nowIso,
  });
  return nextCache;
}

async function decryptVaultCache(
  cache: StoredVaultCache,
  key: SymmetricKey,
  lastSyncAt: string | null,
): Promise<DecryptedVault> {
  const [folders, ciphers] = await Promise.all([
    Promise.all(
      cache.folders.map(async (f) => {
        try {
          return await decryptFolder(f, key);
        } catch {
          return { ...f, name: "[Không giải mã được]" };
        }
      }),
    ),
    Promise.all(
      cache.ciphers.map(async (c) => {
        try {
          return await decryptCipher(c, key);
        } catch {
          return { ...c, name: "[Không giải mã được]" };
        }
      }),
    ),
  ]);

  return { folders, ciphers, lastSyncAt };
}

async function updateBadgeForTab(tabId: number, url?: string): Promise<void> {
  if (!url || (!url.startsWith("http://") && !url.startsWith("https://"))) {
    await chrome.action.setBadgeText({ tabId, text: "" });
    return;
  }

  const [account, secrets, cache] = await Promise.all([
    getAccount(),
    getSessionSecrets(),
    getVaultCache(),
  ]);

  if (!account || !secrets || !cache) {
    await chrome.action.setBadgeText({ tabId, text: "" });
    return;
  }

  try {
    const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
    const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
    const matches = findMatchingLogins(decrypted.ciphers, url);
    await chrome.action.setBadgeBackgroundColor({ tabId, color: "#0572EC" });
    await chrome.action.setBadgeTextColor({ tabId, color: "#FFFFFF" });
    await chrome.action.setBadgeText({
      tabId,
      text: matches.length > 0 ? String(Math.min(matches.length, 99)) : "",
    });
  } catch {
    await chrome.action.setBadgeText({ tabId, text: "" });
  }
}

async function refreshActiveTabBadge(): Promise<void> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tab?.id) {
    await updateBadgeForTab(tab.id, tab.url);
  }
}

async function setupContextMenus(): Promise<void> {
  await chrome.contextMenus.removeAll();
  chrome.contextMenus.create({
    id: CONTEXT_MENUS.OPEN_SIDE_PANEL,
    title: "Mở OpenVault ở Side Panel",
    contexts: ["action", "page"],
  });
  chrome.contextMenus.create({
    id: CONTEXT_MENUS.AUTOFILL_LOGIN,
    title: "OpenVault: Tự động điền tài khoản",
    contexts: ["editable", "page"],
  });
  chrome.contextMenus.create({
    id: CONTEXT_MENUS.GENERATE_PASSWORD,
    title: "OpenVault: Tạo & sao chép mật khẩu mạnh",
    contexts: ["editable", "page"],
  });
}

async function autofillCandidateInTab(tabId: number, cipherId?: string): Promise<boolean> {
  const { key, account } = await getUnlockedKey();
  const tab = await chrome.tabs.get(tabId);
  if (!tab.url) return false;

  const cache = (await getVaultCache()) ?? (await syncEncryptedVaultFromServer(true));
  const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);

  let target = decrypted.ciphers.find((c) => c.id === cipherId && !c.deletedDate);
  if (!target) {
    const matches = findMatchingLogins(decrypted.ciphers, tab.url);
    const top = matches[0];
    if (!top) return false;
    target = decrypted.ciphers.find((c) => c.id === top.id);
  }

  if (!target?.login) return false;

  await chrome.tabs.sendMessage(tabId, {
    type: "OV_FILL_CREDENTIALS",
    username: target.login.username ?? "",
    password: target.login.password ?? "",
    totp: target.login.totp ?? null,
    itemName: target.name,
  });
  return true;
}

// --- Message Handler ---

async function handleMessage(
  request: BgRequest,
  sender: chrome.runtime.MessageSender,
): Promise<BgResponse> {
  switch (request.type) {
    case "GET_STATUS": {
      const [account, secrets, settings, pendingSave] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getPendingSave(),
      ]);
      const data: VaultStatusResponse = {
        hasAccount: account !== null,
        unlocked: account !== null && secrets !== null,
        email: account?.email ?? null,
        serverUrl: account?.serverUrl ?? DEFAULT_SERVER_URL,
        lastSyncAt: account?.lastSyncAt ?? null,
        settings,
        pendingSave,
      };
      return { ok: true, data };
    }

    case "REGISTER": {
      const serverUrl = normalizeServerUrl(request.serverUrl || DEFAULT_SERVER_URL);
      const email = request.email.trim().toLowerCase();
      const kdfConfig = DEFAULT_ARGON2ID_CONFIG;

      const masterKey = await deriveMasterKey(request.masterPassword, email, kdfConfig);
      const masterPasswordHash = await deriveMasterPasswordHash(masterKey, request.masterPassword, 1);
      const stretched = await stretchMasterKey(masterKey);
      const userKey = generateUserSymmetricKey();
      const protectedKey = await protectSymmetricKey(userKey, stretched);
      const rsa = await generateProtectedRsaKeyPair(userKey);

      await apiRegister(serverUrl, {
        email,
        name: request.name.trim() || email.split("@")[0] || "OpenVault User",
        masterPasswordHash,
        masterPasswordHint: request.masterPasswordHint?.trim() || undefined,
        userSymmetricKey: protectedKey,
        kdf: kdfConfig.kdf,
        kdfIterations: kdfConfig.kdfIterations,
        kdfMemory: kdfConfig.kdfMemory,
        kdfParallelism: kdfConfig.kdfParallelism,
        userAsymmetricKeys: rsa,
      });

      const deviceId = await getDeviceId();
      const token = await apiLoginPassword(serverUrl, email, masterPasswordHash, deviceId);

      await setAccount({
        serverUrl,
        email,
        name: request.name.trim() || null,
        kdfConfig,
        protectedKey: token.Key,
        encryptedPrivateKey: token.PrivateKey,
        refreshToken: token.refresh_token,
        lastRevisionMs: 0,
        lastSyncAt: null,
      });
      await setSessionSecrets({
        userKeyB64: bytesToBase64(userKey.raw),
        accessToken: token.access_token,
        accessTokenExpiresAtMs: Date.now() + token.expires_in * 1000,
        unlockedAtMs: Date.now(),
      });

      await syncEncryptedVaultFromServer(true);
      await scheduleAutoLock();
      await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 5 });
      await refreshActiveTabBadge();

      return { ok: true, data: { email, serverUrl } };
    }

    case "LOGIN": {
      const serverUrl = normalizeServerUrl(request.serverUrl || DEFAULT_SERVER_URL);
      const email = request.email.trim().toLowerCase();
      const kdfConfig = await apiPrelogin(serverUrl, email);

      const masterKey = await deriveMasterKey(request.masterPassword, email, kdfConfig);
      const masterPasswordHash = await deriveMasterPasswordHash(masterKey, request.masterPassword, 1);
      const stretched = await stretchMasterKey(masterKey);

      const deviceId = await getDeviceId();
      const token = await apiLoginPassword(serverUrl, email, masterPasswordHash, deviceId);
      const userKey = await unprotectSymmetricKey(token.Key, stretched);

      await setAccount({
        serverUrl,
        email,
        name: null,
        kdfConfig: {
          kdf: token.Kdf,
          kdfIterations: token.KdfIterations,
          kdfMemory: token.KdfMemory,
          kdfParallelism: token.KdfParallelism,
        },
        protectedKey: token.Key,
        encryptedPrivateKey: token.PrivateKey,
        refreshToken: token.refresh_token,
        lastRevisionMs: 0,
        lastSyncAt: null,
      });
      await setSessionSecrets({
        userKeyB64: bytesToBase64(userKey.raw),
        accessToken: token.access_token,
        accessTokenExpiresAtMs: Date.now() + token.expires_in * 1000,
        unlockedAtMs: Date.now(),
      });

      await syncEncryptedVaultFromServer(true);
      await scheduleAutoLock();
      await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 5 });
      await refreshActiveTabBadge();

      return { ok: true, data: { email, serverUrl } };
    }

    case "UNLOCK": {
      const account = await getAccount();
      if (!account) {
        return { ok: false, error: "Chưa có tài khoản. Vui lòng đăng nhập." };
      }

      const masterKey = await deriveMasterKey(
        request.masterPassword,
        account.email,
        account.kdfConfig,
      );
      const stretched = await stretchMasterKey(masterKey);

      let userKey: SymmetricKey;
      try {
        userKey = await unprotectSymmetricKey(account.protectedKey, stretched);
      } catch {
        return { ok: false, error: "Master Password không chính xác." };
      }

      // Refresh access token if online; allow offline unlock with cached vault if network fails
      let accessToken = "";
      let expiresAtMs = 0;
      try {
        if (account.refreshToken) {
          const refreshed = await apiRefreshToken(account.serverUrl, account.refreshToken);
          accessToken = refreshed.access_token;
          expiresAtMs = Date.now() + refreshed.expires_in * 1000;
          await setAccount({ ...account, refreshToken: refreshed.refresh_token });
        } else {
          const mpHash = await deriveMasterPasswordHash(masterKey, request.masterPassword, 1);
          const token = await apiLoginPassword(
            account.serverUrl,
            account.email,
            mpHash,
            await getDeviceId(),
          );
          accessToken = token.access_token;
          expiresAtMs = Date.now() + token.expires_in * 1000;
          await setAccount({ ...account, refreshToken: token.refresh_token });
        }
      } catch {
        // Offline unlock still works against locally cached encrypted vault
      }

      await setSessionSecrets({
        userKeyB64: bytesToBase64(userKey.raw),
        accessToken,
        accessTokenExpiresAtMs: expiresAtMs,
        unlockedAtMs: Date.now(),
      });

      if (accessToken) {
        await syncEncryptedVaultFromServer(false).catch(() => undefined);
      }
      await scheduleAutoLock();
      await refreshActiveTabBadge();

      return { ok: true, data: { unlocked: true } };
    }

    case "LOCK": {
      await setSessionSecrets(null);
      await chrome.alarms.clear(ALARMS.AUTO_LOCK);
      await refreshActiveTabBadge();
      return { ok: true, data: { locked: true } };
    }

    case "LOGOUT": {
      const [account, secrets] = await Promise.all([getAccount(), getSessionSecrets()]);
      if (account && secrets?.accessToken) {
        await apiLogout(
          account.serverUrl,
          secrets.accessToken,
          account.refreshToken,
          request.allDevices ?? false,
        ).catch(() => undefined);
      }
      await Promise.all([
        setSessionSecrets(null),
        setAccount(null),
        setVaultCache(null),
        chrome.storage.session.remove(SESSION_KEYS.PENDING_SAVE),
        chrome.alarms.clearAll(),
      ]);
      await refreshActiveTabBadge();
      return { ok: true, data: { loggedOut: true } };
    }

    case "GET_VAULT": {
      const { key, account } = await getUnlockedKey();
      let cache = await getVaultCache();
      if (request.forceSync || !cache) {
        try {
          cache = await syncEncryptedVaultFromServer(Boolean(request.forceSync));
        } catch (err) {
          if (!cache) throw err;
        }
      }
      const updatedAccount = (await getAccount()) ?? account;
      const decrypted = await decryptVaultCache(cache, key, updatedAccount.lastSyncAt);
      return { ok: true, data: decrypted };
    }

    case "SAVE_CIPHER": {
      const { key } = await getUnlockedKey();
      const encrypted = await encryptCipher(request.cipher, key);
      const saved = request.cipher.id
        ? await withValidAccessToken((url, token) =>
            apiUpdateCipher(url, token, request.cipher.id!, encrypted),
          )
        : await withValidAccessToken((url, token) => apiCreateCipher(url, token, encrypted));

      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return { ok: true, data: await decryptCipher(saved, key) };
    }

    case "DELETE_CIPHER": {
      await getUnlockedKey();
      if (request.permanent) {
        await withValidAccessToken((url, token) => apiHardDeleteCipher(url, token, request.id));
      } else {
        await withValidAccessToken((url, token) => apiSoftDeleteCipher(url, token, request.id));
      }
      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return { ok: true, data: { id: request.id } };
    }

    case "RESTORE_CIPHER": {
      await getUnlockedKey();
      await withValidAccessToken((url, token) => apiRestoreCipher(url, token, request.id));
      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return { ok: true, data: { id: request.id } };
    }

    case "SAVE_FOLDER": {
      const { key } = await getUnlockedKey();
      const encName = await encryptString(request.folder.name, key);
      const saved = request.folder.id
        ? await withValidAccessToken((url, token) =>
            apiUpdateFolder(url, token, request.folder.id!, encName),
          )
        : await withValidAccessToken((url, token) => apiCreateFolder(url, token, encName));

      await syncEncryptedVaultFromServer(true);
      return { ok: true, data: await decryptFolder(saved, key) };
    }

    case "DELETE_FOLDER": {
      await getUnlockedKey();
      await withValidAccessToken((url, token) => apiDeleteFolder(url, token, request.id));
      await syncEncryptedVaultFromServer(true);
      return { ok: true, data: { id: request.id } };
    }

    case "IMPORT_BITWARDEN_JSON": {
      const { key } = await getUnlockedKey();
      const parsed = JSON.parse(request.rawJson) as BitwardenExportJson;
      const encryptedPayload = await prepareBitwardenImport(parsed, key);
      const res = await withValidAccessToken((url, token) =>
        apiImportVault(url, token, encryptedPayload),
      );
      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return { ok: true, data: res };
    }

    case "EXPORT_ENCRYPTED_BACKUP": {
      const { account } = await getUnlockedKey();
      const cache = (await getVaultCache()) ?? (await syncEncryptedVaultFromServer(true));
      const backup = {
        format: "openvault-encrypted-export",
        version: 1,
        exportedAt: new Date().toISOString(),
        email: account.email,
        kdf: account.kdfConfig,
        protectedKey: account.protectedKey,
        folders: cache.folders,
        ciphers: cache.ciphers,
      };
      return { ok: true, data: JSON.stringify(backup, null, 2) };
    }

    case "TRIGGER_R2_BACKUP": {
      await getUnlockedKey();
      const result = await withValidAccessToken((url, token) => apiTriggerR2Backup(url, token));
      return { ok: true, data: result };
    }

    case "LIST_R2_BACKUPS": {
      await getUnlockedKey();
      const result = await withValidAccessToken((url, token) => apiListR2Backups(url, token));
      return { ok: true, data: result.backups };
    }

    case "UPDATE_SETTINGS": {
      const current = await getSettings();
      const next = { ...current, ...request.settings };
      await chrome.storage.local.set({ [LOCAL_KEYS.SETTINGS]: next });
      await scheduleAutoLock();
      return { ok: true, data: next };
    }

    case "GET_MATCHES_FOR_URL": {
      const [account, secrets, settings, cache] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getVaultCache(),
      ]);
      if (!settings.showInlineAutofill) {
        return { ok: true, data: { unlocked: Boolean(secrets), disabled: true, matches: [] } };
      }
      if (!account || !secrets || !cache) {
        return { ok: true, data: { unlocked: false, disabled: false, matches: [] } };
      }
      const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
      const matches = findMatchingLogins(decrypted.ciphers, request.url);
      return { ok: true, data: { unlocked: true, disabled: false, matches } };
    }

    case "AUTOFILL_IN_TAB": {
      const filled = await autofillCandidateInTab(request.tabId, request.cipherId);
      return { ok: true, data: { filled } };
    }

    case "CAPTURE_LOGIN_SUBMIT": {
      const [account, secrets, settings, cache] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getVaultCache(),
      ]);
      if (!settings.promptSaveCredentials || !account || !secrets || !cache) {
        return { ok: true, data: { prompt: false } };
      }

      // Check if an identical username + password is already stored for this domain
      const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
      const existingMatches = findMatchingLogins(decrypted.ciphers, request.payload.url);
      const alreadySaved = existingMatches.some(
        (m) =>
          m.username.trim().toLowerCase() === request.payload.username.trim().toLowerCase() &&
          m.password === request.payload.password,
      );
      if (alreadySaved) {
        return { ok: true, data: { prompt: false } };
      }

      const pending: PendingSaveCredential = {
        ...request.payload,
        detectedAt: Date.now(),
      };
      await chrome.storage.session.set({ [SESSION_KEYS.PENDING_SAVE]: pending });

      if (sender.tab?.id) {
        await chrome.tabs
          .sendMessage(sender.tab.id, {
            type: "OV_SHOW_SAVE_BANNER",
            pending,
          })
          .catch(() => undefined);
      }
      return { ok: true, data: { prompt: true } };
    }

    case "DISMISS_PENDING_SAVE": {
      await chrome.storage.session.remove(SESSION_KEYS.PENDING_SAVE);
      return { ok: true, data: { dismissed: true } };
    }

    case "SAVE_PENDING_CREDENTIAL": {
      const { key } = await getUnlockedKey();
      const pending = await getPendingSave();
      if (!pending) return { ok: false, error: "Không có tài khoản chờ lưu." };

      const cipher: CipherPayload = {
        type: CipherType.Login,
        name: pending.title || new URL(pending.url).hostname.replace(/^www\./, ""),
        favorite: false,
        login: {
          uris: [{ uri: pending.origin, match: null }],
          username: pending.username,
          password: pending.password,
          totp: null,
        },
      };
      const encrypted = await encryptCipher(cipher, key);
      await withValidAccessToken((url, token) => apiCreateCipher(url, token, encrypted));
      await chrome.storage.session.remove(SESSION_KEYS.PENDING_SAVE);
      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return { ok: true, data: { saved: true } };
    }

    case "TOUCH_ACTIVITY": {
      const secrets = await getSessionSecrets();
      if (secrets) await scheduleAutoLock();
      return { ok: true, data: { ok: true } };
    }
  }
}

// --- Top-level synchronous MV3 Event Listeners ---

chrome.runtime.onInstalled.addListener(() => {
  void (async () => {
    await ensureSessionAccessPolicy();
    await setupContextMenus();
    await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 5 });
  })();
});

chrome.runtime.onStartup.addListener(() => {
  void (async () => {
    await ensureSessionAccessPolicy();
    await setupContextMenus();
  })();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  void (async () => {
    if (alarm.name === ALARMS.AUTO_LOCK) {
      await setSessionSecrets(null);
      await refreshActiveTabBadge();
    } else if (alarm.name === ALARMS.SYNC_POLL) {
      const secrets = await getSessionSecrets();
      if (secrets) {
        await syncEncryptedVaultFromServer(false).catch(() => undefined);
        await refreshActiveTabBadge();
      }
    }
  })();
});

chrome.tabs.onActivated.addListener((info) => {
  void (async () => {
    try {
      const tab = await chrome.tabs.get(info.tabId);
      await updateBadgeForTab(info.tabId, tab.url);
    } catch {
      // Tab closed before query completed
    }
  })();
});

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === "complete" && tab.url) {
    void updateBadgeForTab(tabId, tab.url);
  }
});

chrome.commands.onCommand.addListener((command) => {
  void (async () => {
    if (command === "autofill-current-tab") {
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
      if (tab?.id) {
        try {
          await autofillCandidateInTab(tab.id);
        } catch {
          // Vault locked or no matching login
        }
      }
    }
  })();
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  void (async () => {
    if (info.menuItemId === CONTEXT_MENUS.OPEN_SIDE_PANEL && tab?.windowId) {
      await chrome.sidePanel.open({ windowId: tab.windowId });
      return;
    }
    if (info.menuItemId === CONTEXT_MENUS.AUTOFILL_LOGIN && tab?.id) {
      try {
        const filled = await autofillCandidateInTab(tab.id);
        await chrome.tabs.sendMessage(tab.id, {
          type: "OV_SHOW_TOAST",
          message: filled
            ? "OpenVault đã điền tài khoản"
            : "Không tìm thấy tài khoản khớp cho trang này",
        });
      } catch {
        await chrome.tabs.sendMessage(tab.id, {
          type: "OV_SHOW_TOAST",
          message: "Vui lòng mở khóa OpenVault trước khi điền",
        });
      }
      return;
    }
    if (info.menuItemId === CONTEXT_MENUS.GENERATE_PASSWORD && tab?.id) {
      const pw = generatePassword({
        length: 20,
        lowercase: true,
        uppercase: true,
        digits: true,
        symbols: true,
      });
      await chrome.tabs
        .sendMessage(tab.id, {
          type: "OV_COPY_AND_TOAST",
          text: pw,
          message: "Đã tạo và sao chép mật khẩu mạnh 20 ký tự",
        })
        .catch(() => undefined);
    }
  })();
});

chrome.runtime.onMessage.addListener((message: BgRequest, sender, sendResponse) => {
  void (async () => {
    try {
      const response = await handleMessage(message, sender);
      sendResponse(response);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Đã xảy ra lỗi không xác định";
      sendResponse({ ok: false, error: msg } satisfies BgResponse);
    }
  })();
  return true;
});
