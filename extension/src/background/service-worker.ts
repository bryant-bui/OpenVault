import {
  CipherType,
  DEFAULT_ARGON2ID_CONFIG,
  MAX_PIN_ATTEMPTS,
  base64ToBytes,
  base64UrlToBytes,
  bytesToBase64,
  bytesToBase64Url,
  createPasskeyCredential,
  decryptCipher,
  decryptFolder,
  decryptString,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptCipher,
  encryptString,
  generatePassword,
  generateProtectedRsaKeyPair,
  generateTotp,
  generateUserSymmetricKey,
  prepareUniversalVaultImport,
  protectSymmetricKey,
  protectUserKeyWithPin,
  signPasskeyAssertion,
  splitSymmetricKey,
  stretchMasterKey,
  unprotectSymmetricKey,
  unprotectUserKeyWithPin,
  utf8ToBytes,
  type CipherPayload,
  type Fido2Credential,
  type PinProtectedRecord,
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
import {
  isValidRpIdForOrigin,
  type PasskeyMatchSummary,
  type PasskeyPrepareCreateResponse,
  type PasskeyPrepareGetResponse,
  type SerializedCreateResponseData,
  type SerializedGetResponseData,
} from "../shared/passkey-bridge.js";
import type {
  AutofillContextResponse,
  BgRequest,
  BgResponse,
  CardAutofillCandidate,
  DecryptedVault,
  ExtensionSettings,
  IdentityAutofillCandidate,
  PendingSaveCredential,
  SessionSecrets,
  StoredAccount,
  StoredPinState,
  StoredVaultCache,
  TotpAutofillCandidate,
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

async function getPinState(): Promise<{
  localState: StoredPinState | null;
  sessionRecord: PinProtectedRecord | null;
}> {
  const [localRes, sessionRes] = await Promise.all([
    chrome.storage.local.get(LOCAL_KEYS.PIN_LOCAL),
    chrome.storage.session.get(SESSION_KEYS.PIN_SESSION),
  ]);
  return {
    localState: (localRes[LOCAL_KEYS.PIN_LOCAL] as StoredPinState | undefined) ?? null,
    sessionRecord: (sessionRes[SESSION_KEYS.PIN_SESSION] as PinProtectedRecord | undefined) ?? null,
  };
}

async function clearAllPinData(): Promise<void> {
  await Promise.all([
    chrome.storage.local.remove(LOCAL_KEYS.PIN_LOCAL),
    chrome.storage.session.remove(SESSION_KEYS.PIN_SESSION),
  ]);
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
  if (!account) throw new Error("Not signed in.");

  const cached = await getVaultCache();
  let remoteRevision = 0;
  if (cached) {
    try {
      remoteRevision = await withValidAccessToken((url, token) =>
        apiGetRevisionDate(url, token),
      );
      if (!force && remoteRevision <= account.lastRevisionMs) {
        return cached;
      }
    } catch {
      if (!force) {
        // Offline fallback: return cached encrypted vault
        return cached;
      }
    }
  }

  const syncData = await withValidAccessToken((url, token) => apiSync(url, token));
  if (remoteRevision === 0) {
    remoteRevision = await withValidAccessToken((url, token) =>
      apiGetRevisionDate(url, token),
    ).catch(() => Date.now());
  }
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
    lastRevisionMs: remoteRevision,
    lastSyncAt: nowIso,
  });
  // Notify any open popup or sidepanel so remote edits appear immediately
  chrome.runtime.sendMessage({ type: "OV_VAULT_UPDATED" }).catch(() => undefined);
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

async function writeClipboardViaOffscreen(text: string): Promise<boolean> {
  if (!chrome.offscreen) return false;
  const offscreenUrl = chrome.runtime.getURL("offscreen.html");
  let createdHere = false;
  try {
    const existing = await chrome.runtime.getContexts({
      contextTypes: [chrome.runtime.ContextType.OFFSCREEN_DOCUMENT],
      documentUrls: [offscreenUrl],
    });
    if (existing.length === 0) {
      try {
        await chrome.offscreen.createDocument({
          url: "offscreen.html",
          reasons: [chrome.offscreen.Reason.CLIPBOARD],
          justification: "Copy 2FA codes and clear sensitive passwords from clipboard automatically.",
        });
        createdHere = true;
      } catch {
        // Another concurrent call already created the single offscreen document
      }
    }

    const res = (await chrome.runtime.sendMessage({
      target: "offscreen",
      type: "OV_OFFSCREEN_CLIPBOARD",
      text,
    })) as { ok?: boolean } | undefined;
    return Boolean(res?.ok);
  } catch {
    return false;
  } finally {
    if (createdHere) {
      await chrome.offscreen.closeDocument().catch(() => undefined);
    }
  }
}

async function scheduleClipboardClear(): Promise<void> {
  const settings = await getSettings();
  await chrome.alarms.clear(ALARMS.CLEAR_CLIPBOARD);
  if (settings.clipboardClearSeconds > 0) {
    await chrome.alarms.create(ALARMS.CLEAR_CLIPBOARD, {
      when: Date.now() + settings.clipboardClearSeconds * 1000,
    });
  }
}

async function applyBrowserPasswordManagerPreference(enabled: boolean): Promise<void> {
  const services = chrome.privacy?.services;
  if (!services) return;
  try {
    if (enabled) {
      await Promise.allSettled([
        services.passwordSavingEnabled?.set({ value: false }),
        services.autofillCreditCardEnabled?.set({ value: false }),
        services.autofillAddressEnabled?.set({ value: false }),
      ]);
    } else {
      await Promise.allSettled([
        services.passwordSavingEnabled?.clear({}),
        services.autofillCreditCardEnabled?.clear({}),
        services.autofillAddressEnabled?.clear({}),
      ]);
    }
  } catch {
    // Enterprise policy or unsupported browser
  }
}

function buildCardCandidates(ciphers: CipherPayload[]): CardAutofillCandidate[] {
  return ciphers
    .filter((c) => !c.deletedDate && c.type === CipherType.Card && c.card && c.id)
    .map((c) => ({
      id: c.id!,
      name: c.name,
      cardholderName: c.card?.cardholderName ?? "",
      brand: c.card?.brand ?? "",
      number: c.card?.number ?? "",
      expMonth: c.card?.expMonth ?? "",
      expYear: c.card?.expYear ?? "",
      code: c.card?.code ?? "",
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

function buildIdentityCandidates(ciphers: CipherPayload[]): IdentityAutofillCandidate[] {
  return ciphers
    .filter((c) => !c.deletedDate && c.type === CipherType.Identity && c.identity && c.id)
    .map((c) => {
      const idn = c.identity!;
      const firstName = idn.firstName ?? "";
      const middleName = idn.middleName ?? "";
      const lastName = idn.lastName ?? "";
      const fullName = [firstName, middleName, lastName].filter(Boolean).join(" ").trim() || c.name;
      return {
        id: c.id!,
        name: c.name,
        firstName,
        middleName,
        lastName,
        fullName,
        email: idn.email ?? "",
        phone: idn.phone ?? "",
        company: idn.company ?? "",
        address1: idn.address1 ?? "",
        address2: idn.address2 ?? "",
        city: idn.city ?? "",
        state: idn.state ?? "",
        postalCode: idn.postalCode ?? "",
        country: idn.country ?? "",
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name));
}

async function buildTotpCandidates(
  ciphers: CipherPayload[],
  url: string,
): Promise<TotpAutofillCandidate[]> {
  const loginMatches = findMatchingLogins(ciphers, url).filter((m) => Boolean(m.totp));
  const out: TotpAutofillCandidate[] = [];
  for (const m of loginMatches) {
    if (!m.totp) continue;
    try {
      const generated = await generateTotp(m.totp);
      out.push({
        id: m.id,
        name: m.name,
        username: m.username,
        code: generated.code,
        remainingSeconds: generated.remainingSeconds,
        period: generated.period,
      });
    } catch {
      // Skip malformed TOTP secret
    }
  }
  return out;
}

async function autoCopyTotpForCipher(totpSecret: string | null | undefined): Promise<string | null> {
  if (!totpSecret) return null;
  const settings = await getSettings();
  if (!settings.autoCopyTotp) return null;
  try {
    const { code } = await generateTotp(totpSecret);
    const ok = await writeClipboardViaOffscreen(code);
    if (ok) {
      await scheduleClipboardClear();
      return code;
    }
  } catch {
    // Ignore malformed TOTP secret
  }
  return null;
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

  if (!target) return false;

  if (target.type === CipherType.Card && target.card && target.id) {
    const card: CardAutofillCandidate = {
      id: target.id,
      name: target.name,
      cardholderName: target.card.cardholderName ?? "",
      brand: target.card.brand ?? "",
      number: target.card.number ?? "",
      expMonth: target.card.expMonth ?? "",
      expYear: target.card.expYear ?? "",
      code: target.card.code ?? "",
    };
    const res = (await chrome.tabs.sendMessage(tabId, {
      type: "OV_FILL_CARD",
      card,
    })) as { ok?: boolean } | undefined;
    return Boolean(res?.ok);
  }

  if (target.type === CipherType.Identity && target.identity && target.id) {
    const [identity] = buildIdentityCandidates([target]);
    if (!identity) return false;
    const res = (await chrome.tabs.sendMessage(tabId, {
      type: "OV_FILL_IDENTITY",
      identity,
    })) as { ok?: boolean } | undefined;
    return Boolean(res?.ok);
  }

  if (!target.login) return false;

  const copiedTotpCode = await autoCopyTotpForCipher(target.login.totp);

  const res = (await chrome.tabs.sendMessage(tabId, {
    type: "OV_FILL_CREDENTIALS",
    username: target.login.username ?? "",
    password: target.login.password ?? "",
    totp: target.login.totp ?? null,
    copiedTotpCode,
    itemName: target.name,
  })) as { ok?: boolean } | undefined;
  return Boolean(res?.ok ?? true);
}

function verifySenderPasskeyOrigin(
  rpId: string,
  claimedOrigin: string,
  sender: chrome.runtime.MessageSender,
): boolean {
  if (!isValidRpIdForOrigin(rpId, claimedOrigin)) return false;
  if (sender.url) {
    try {
      const senderOrigin = new URL(sender.url).origin;
      if (senderOrigin !== claimedOrigin) return false;
    } catch {
      return false;
    }
  }
  return true;
}

function findMatchingPasskeys(
  ciphers: CipherPayload[],
  rpId: string,
  allowCredentialIds: string[] = [],
): Array<{ cipher: CipherPayload; credential: Fido2Credential }> {
  const cleanRp = rpId.trim().toLowerCase().replace(/\.$/, "");
  const allowSet = allowCredentialIds.length > 0 ? new Set(allowCredentialIds) : null;
  const results: Array<{ cipher: CipherPayload; credential: Fido2Credential }> = [];

  for (const cipher of ciphers) {
    if (cipher.deletedDate || cipher.type !== CipherType.Login || !cipher.id) continue;
    const creds = cipher.login?.fido2Credentials;
    if (!creds || creds.length === 0) continue;
    for (const credential of creds) {
      if (credential.rpId.trim().toLowerCase().replace(/\.$/, "") !== cleanRp) continue;
      if (allowSet && !allowSet.has(credential.credentialId)) continue;
      results.push({ cipher, credential });
    }
  }

  results.sort((a, b) => {
    const favDiff = Number(Boolean(b.cipher.favorite)) - Number(Boolean(a.cipher.favorite));
    if (favDiff !== 0) return favDiff;
    return (b.credential.creationDate ?? "").localeCompare(a.credential.creationDate ?? "");
  });
  return results;
}

// --- Message Handler ---

async function handleMessage(
  request: BgRequest,
  sender: chrome.runtime.MessageSender,
): Promise<BgResponse> {
  switch (request.type) {
    case "GET_STATUS": {
      const [account, secrets, settings, pendingSave, pinInfo] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getPendingSave(),
        getPinState(),
      ]);
      const pinState = pinInfo.localState;
      const activeRecord = pinState
        ? pinState.requireMasterOnRestart
          ? pinInfo.sessionRecord
          : pinState.record
        : null;
      const remainingAttempts = pinState
        ? Math.max(0, pinState.maxAttempts - pinState.failedAttempts)
        : settings.pinMaxAttempts;

      const data: VaultStatusResponse = {
        hasAccount: account !== null,
        unlocked: account !== null && secrets !== null,
        email: account?.email ?? null,
        serverUrl: account?.serverUrl ?? DEFAULT_SERVER_URL,
        lastSyncAt: account?.lastSyncAt ?? null,
        settings,
        pendingSave,
        pinEnabled: pinState !== null,
        pinAvailableNow: activeRecord !== null && remainingAttempts > 0,
        pinRequireMasterOnRestart: pinState?.requireMasterOnRestart ?? true,
        pinRemainingAttempts: remainingAttempts,
        pinMaxAttempts: pinState?.maxAttempts ?? settings.pinMaxAttempts,
        pinLength: pinState?.pinLength ?? 6,
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

      await clearAllPinData();
      await syncEncryptedVaultFromServer(true);
      await scheduleAutoLock();
      await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 0.5 });
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

      await clearAllPinData();
      await syncEncryptedVaultFromServer(true);
      await scheduleAutoLock();
      await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 0.5 });
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

      // Reset PIN failure counter & restore RAM-only PIN record after browser restart
      const { localState, sessionRecord } = await getPinState();
      if (localState) {
        if (localState.failedAttempts > 0) {
          await chrome.storage.local.set({
            [LOCAL_KEYS.PIN_LOCAL]: { ...localState, failedAttempts: 0 },
          });
        }
        if (
          localState.requireMasterOnRestart &&
          !sessionRecord &&
          localState.encryptedSessionRecord
        ) {
          try {
            const json = await decryptString(localState.encryptedSessionRecord, userKey);
            const restored = JSON.parse(json) as PinProtectedRecord;
            if (restored.saltB64 && restored.protectedKey && restored.iterations > 0) {
              await chrome.storage.session.set({ [SESSION_KEYS.PIN_SESSION]: restored });
            }
          } catch {
            // Ignore corrupted record; user can re-configure PIN in Settings
          }
        }
      }

      if (accessToken) {
        await syncEncryptedVaultFromServer(false).catch(() => undefined);
      }
      await scheduleAutoLock();
      await refreshActiveTabBadge();

      return { ok: true, data: { unlocked: true } };
    }

    case "SET_PIN": {
      const [{ key }, settings] = await Promise.all([getUnlockedKey(), getSettings()]);
      const record = await protectUserKeyWithPin(key, request.pin);
      const encryptedSessionRecord = request.requireMasterOnRestart
        ? await encryptString(JSON.stringify(record), key)
        : null;
      const maxAttempts = Math.min(
        10,
        Math.max(3, request.maxAttempts ?? settings.pinMaxAttempts ?? MAX_PIN_ATTEMPTS),
      );
      const nextLocalState: StoredPinState = {
        record: request.requireMasterOnRestart ? null : record,
        encryptedSessionRecord,
        requireMasterOnRestart: request.requireMasterOnRestart,
        failedAttempts: 0,
        maxAttempts,
        pinLength: request.pin.length,
      };
      await chrome.storage.local.set({ [LOCAL_KEYS.PIN_LOCAL]: nextLocalState });
      if (request.requireMasterOnRestart) {
        await chrome.storage.session.set({ [SESSION_KEYS.PIN_SESSION]: record });
      } else {
        await chrome.storage.session.remove(SESSION_KEYS.PIN_SESSION);
      }
      return { ok: true, data: { pinEnabled: true } };
    }

    case "DISABLE_PIN": {
      await getUnlockedKey();
      await clearAllPinData();
      return { ok: true, data: { pinEnabled: false } };
    }

    case "UNLOCK_WITH_PIN": {
      const [account, pinInfo] = await Promise.all([getAccount(), getPinState()]);
      if (!account) {
        return { ok: false, error: "Chưa có tài khoản. Vui lòng đăng nhập." };
      }

      const localState = pinInfo.localState;
      const activeRecord = localState
        ? localState.requireMasterOnRestart
          ? pinInfo.sessionRecord
          : localState.record
        : null;

      if (!localState || !activeRecord) {
        return {
          ok: false,
          error: "Mã PIN chưa sẵn sàng (cần mở bằng Master Password sau khi khởi động lại Chrome).",
        };
      }

      let userKey: SymmetricKey;
      try {
        userKey = await unprotectUserKeyWithPin(activeRecord, request.pin);
      } catch {
        const nextFailed = localState.failedAttempts + 1;
        if (nextFailed >= localState.maxAttempts) {
          await clearAllPinData();
          return {
            ok: false,
            error: `Sai mã PIN ${localState.maxAttempts} lần liên tiếp — đã tự động hủy mã PIN vì bảo mật. Vui lòng mở khóa bằng Master Password.`,
          };
        }
        await chrome.storage.local.set({
          [LOCAL_KEYS.PIN_LOCAL]: { ...localState, failedAttempts: nextFailed },
        });
        const remaining = localState.maxAttempts - nextFailed;
        return {
          ok: false,
          error: `Mã PIN không chính xác (còn ${remaining}/${localState.maxAttempts} lần thử).`,
        };
      }

      // Reset failure counter & remember PIN length on success (for seamless auto-unlock)
      if (localState.failedAttempts > 0 || localState.pinLength !== request.pin.length) {
        await chrome.storage.local.set({
          [LOCAL_KEYS.PIN_LOCAL]: {
            ...localState,
            failedAttempts: 0,
            pinLength: request.pin.length,
          },
        });
      }

      let accessToken = "";
      let expiresAtMs = 0;
      try {
        if (account.refreshToken) {
          const refreshed = await apiRefreshToken(account.serverUrl, account.refreshToken);
          accessToken = refreshed.access_token;
          expiresAtMs = Date.now() + refreshed.expires_in * 1000;
          await setAccount({ ...account, refreshToken: refreshed.refresh_token });
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
        clearAllPinData(),
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
      const prepared = await prepareUniversalVaultImport(request.rawJson, key);
      const { folders: encFolders, ciphers: encCiphers, folderRelationships } = prepared.payload;

      // 1. Create folders first so large imports can be safely chunked within Cloudflare Workers CPU budget
      const createdFolderIds: string[] = [];
      for (const folder of encFolders) {
        const created = await withValidAccessToken((url, token) =>
          apiCreateFolder(url, token, folder.name),
        );
        createdFolderIds.push(created.id ?? "");
      }

      const relMap = new Map<number, string>();
      for (const rel of folderRelationships) {
        const fid = createdFolderIds[rel.value];
        if (fid) relMap.set(rel.key, fid);
      }

      const ciphersWithFolders: CipherPayload[] = encCiphers.map((cipher, idx) => ({
        ...cipher,
        folderId: relMap.get(idx) ?? null,
      }));

      // 2. Upload ciphers in chunks of 40 per Worker request (fast & safe under 10ms Workers Free CPU)
      const CHUNK_SIZE = 40;
      let importedCiphers = 0;
      for (let i = 0; i < ciphersWithFolders.length; i += CHUNK_SIZE) {
        const chunk = ciphersWithFolders.slice(i, i + CHUNK_SIZE);
        const res = await withValidAccessToken((url, token) =>
          apiImportVault(url, token, {
            folders: [],
            ciphers: chunk,
            folderRelationships: [],
          }),
        );
        importedCiphers += res.importedCiphers;
      }

      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();
      return {
        ok: true,
        data: {
          format: prepared.format,
          importedFolders: encFolders.length,
          importedCiphers,
          skippedCount: prepared.skippedCount,
        },
      };
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

      if (typeof request.settings.defaultBrowserPasswordManager === "boolean") {
        await applyBrowserPasswordManagerPreference(next.defaultBrowserPasswordManager);
      }
      if (typeof request.settings.pinMaxAttempts === "number") {
        const { localState } = await getPinState();
        if (localState) {
          await chrome.storage.local.set({
            [LOCAL_KEYS.PIN_LOCAL]: {
              ...localState,
              maxAttempts: Math.min(10, Math.max(3, request.settings.pinMaxAttempts)),
            },
          });
        }
      }
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

    case "GET_AUTOFILL_CONTEXT": {
      const [account, secrets, settings, cache, pinInfo] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getVaultCache(),
        getPinState(),
      ]);

      const kind = request.fieldKind;
      const featureEnabled =
        kind === "login"
          ? settings.showInlineAutofill
          : kind === "new-password"
            ? settings.autosuggestPassword || settings.showInlineAutofill
            : kind === "totp"
              ? settings.autofillTotp
              : kind === "card"
                ? settings.autofillCard
                : settings.autofillIdentity;

      const pinLocal = pinInfo.localState;
      const pinAvailableNow = Boolean(
        account &&
          pinLocal &&
          (pinLocal.requireMasterOnRestart ? pinInfo.sessionRecord : pinLocal.record),
      );
      const pinMaxAttempts = pinLocal?.maxAttempts ?? settings.pinMaxAttempts;
      const pinRemainingAttempts = pinLocal
        ? Math.max(0, pinMaxAttempts - pinLocal.failedAttempts)
        : settings.pinMaxAttempts;

      const baseEmpty: AutofillContextResponse = {
        hasAccount: Boolean(account),
        email: account?.email ?? null,
        unlocked: Boolean(account && secrets && cache),
        disabled: !featureEnabled,
        pinAvailableNow,
        pinLength: pinLocal?.pinLength ?? 6,
        pinRemainingAttempts,
        pinMaxAttempts,
        fieldKind: kind,
        matches: [],
        totpMatches: [],
        cards: [],
        identities: [],
        suggestedPassword: null,
        autoCopyGeneratedPassword: settings.autoCopyGeneratedPassword,
        autoCopyTotp: settings.autoCopyTotp,
      };

      if (!featureEnabled) {
        return { ok: true, data: baseEmpty };
      }

      if (kind === "new-password" && settings.autosuggestPassword) {
        baseEmpty.suggestedPassword = generatePassword({
          length: 20,
          lowercase: true,
          uppercase: true,
          digits: true,
          symbols: true,
        });
      }

      if (!account || !secrets || !cache) {
        return { ok: true, data: baseEmpty };
      }

      const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);

      if (kind === "login") {
        baseEmpty.matches = findMatchingLogins(decrypted.ciphers, request.url);
      } else if (kind === "new-password") {
        baseEmpty.matches = settings.showInlineAutofill
          ? findMatchingLogins(decrypted.ciphers, request.url)
          : [];
      } else if (kind === "totp") {
        baseEmpty.totpMatches = await buildTotpCandidates(decrypted.ciphers, request.url);
      } else if (kind === "card") {
        baseEmpty.cards = buildCardCandidates(decrypted.ciphers);
      } else if (kind === "identity") {
        baseEmpty.identities = buildIdentityCandidates(decrypted.ciphers);
      }

      return { ok: true, data: baseEmpty };
    }

    case "GENERATE_INLINE_PASSWORD": {
      const password = generatePassword({
        length: 20,
        lowercase: true,
        uppercase: true,
        digits: true,
        symbols: true,
      });
      return { ok: true, data: { password } };
    }

    case "NOTIFY_LOGIN_FILLED": {
      const [account, secrets, cache] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getVaultCache(),
      ]);
      if (!account || !secrets || !cache) {
        return { ok: true, data: { copiedTotp: null } };
      }
      await scheduleAutoLock();
      const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
      const target = decrypted.ciphers.find((c) => c.id === request.cipherId && !c.deletedDate);
      const copiedTotp = await autoCopyTotpForCipher(target?.login?.totp);
      return { ok: true, data: { copiedTotp } };
    }

    case "SCHEDULE_CLIPBOARD_CLEAR": {
      if (typeof request.copyText === "string") {
        await writeClipboardViaOffscreen(request.copyText);
      }
      await scheduleClipboardClear();
      return { ok: true, data: { scheduled: true } };
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

    case "PASSKEY_PREPARE_CREATE": {
      const [account, secrets, settings, cache, pinInfo] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getVaultCache(),
        getPinState(),
      ]);

      const validOrigin = verifySenderPasskeyOrigin(request.rpId, request.origin, sender);
      const pinLocal = pinInfo.localState;
      const pinAvailableNow = Boolean(
        account &&
          pinLocal &&
          (pinLocal.requireMasterOnRestart ? pinInfo.sessionRecord : pinLocal.record) &&
          pinLocal.failedAttempts < pinLocal.maxAttempts,
      );

      let alreadyExcluded = false;
      if (
        validOrigin &&
        settings.passkeySaveEnabled &&
        account &&
        secrets &&
        cache &&
        request.excludeCredentialIds.length > 0
      ) {
        const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
        const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
        const excludedMatches = findMatchingPasskeys(
          decrypted.ciphers,
          request.rpId,
          request.excludeCredentialIds,
        );
        alreadyExcluded = excludedMatches.length > 0;
      }

      const data: PasskeyPrepareCreateResponse = {
        enabled: settings.passkeySaveEnabled,
        validOrigin,
        hasAccount: Boolean(account),
        email: account?.email ?? null,
        unlocked: Boolean(account && secrets && cache),
        pinAvailableNow,
        pinLength: pinLocal?.pinLength ?? 6,
        alreadyExcluded,
      };
      return { ok: true, data };
    }

    case "PASSKEY_EXECUTE_CREATE": {
      if (!verifySenderPasskeyOrigin(request.rpId, request.origin, sender)) {
        return { ok: false, error: "Tên miền RP ID không hợp lệ cho trang web hiện tại." };
      }
      const { key, account } = await getUnlockedKey();
      const cache = (await getVaultCache()) ?? (await syncEncryptedVaultFromServer(true));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);

      const cleanRpId = request.rpId.trim().toLowerCase().replace(/\.$/, "");
      const clientDataJson = JSON.stringify({
        type: "webauthn.create",
        challenge: request.challengeB64Url,
        origin: request.origin,
        crossOrigin: false,
      });

      const created = await createPasskeyCredential({
        rpId: cleanRpId,
        rpName: request.rpName || cleanRpId,
        userHandle: base64UrlToBytes(request.userIdB64Url),
        userName: request.userName,
        userDisplayName: request.userDisplayName || request.userName,
        discoverable: request.discoverable,
      });

      // Attach passkey to an existing matching Login cipher if username matches, otherwise create a new Login item
      const normalizedUser = request.userName.trim().toLowerCase();
      const domainMatches = findMatchingLogins(decrypted.ciphers, request.origin);
      let existingCipher: CipherPayload | undefined;

      for (const m of domainMatches) {
        const candidate = decrypted.ciphers.find((c) => c.id === m.id && !c.deletedDate);
        if (!candidate?.login) continue;
        const candUser = (candidate.login.username ?? "").trim().toLowerCase();
        if (normalizedUser && candUser === normalizedUser) {
          existingCipher = candidate;
          break;
        }
      }

      if (!existingCipher) {
        existingCipher = decrypted.ciphers.find(
          (c) =>
            !c.deletedDate &&
            c.type === CipherType.Login &&
            c.login?.fido2Credentials?.some(
              (f) =>
                f.rpId.toLowerCase() === cleanRpId &&
                (f.userName ?? "").trim().toLowerCase() === normalizedUser,
            ),
        );
      }

      if (existingCipher?.id && existingCipher.login) {
        const existingCreds = (existingCipher.login.fido2Credentials ?? []).filter(
          (f) =>
            !(
              f.rpId.toLowerCase() === cleanRpId &&
              f.userHandle === created.credential.userHandle
            ),
        );
        const updatedCipher: CipherPayload = {
          ...existingCipher,
          login: {
            ...existingCipher.login,
            username: existingCipher.login.username || request.userName,
            fido2Credentials: [...existingCreds, created.credential],
          },
        };
        const encrypted = await encryptCipher(updatedCipher, key);
        await withValidAccessToken((url, token) =>
          apiUpdateCipher(url, token, existingCipher.id!, encrypted),
        );
      } else {
        const newCipher: CipherPayload = {
          type: CipherType.Login,
          name: request.rpName || cleanRpId,
          favorite: false,
          login: {
            uris: [{ uri: request.origin, match: null }],
            username: request.userName || null,
            password: null,
            totp: null,
            fido2Credentials: [created.credential],
          },
        };
        const encrypted = await encryptCipher(newCipher, key);
        await withValidAccessToken((url, token) => apiCreateCipher(url, token, encrypted));
      }

      await syncEncryptedVaultFromServer(true);
      await refreshActiveTabBadge();

      const data: SerializedCreateResponseData = {
        credentialIdB64Url: created.credential.credentialId,
        clientDataJsonB64Url: bytesToBase64Url(utf8ToBytes(clientDataJson)),
        attestationObjectB64Url: bytesToBase64Url(created.attestationObject),
        authenticatorDataB64Url: bytesToBase64Url(created.authenticatorData),
        publicKeySpkiB64Url: bytesToBase64Url(created.publicKeySpki),
      };
      return { ok: true, data };
    }

    case "PASSKEY_PREPARE_GET": {
      const [account, secrets, settings, cache, pinInfo] = await Promise.all([
        getAccount(),
        getSessionSecrets(),
        getSettings(),
        getVaultCache(),
        getPinState(),
      ]);

      const validOrigin = verifySenderPasskeyOrigin(request.rpId, request.origin, sender);
      const pinLocal = pinInfo.localState;
      const pinAvailableNow = Boolean(
        account &&
          pinLocal &&
          (pinLocal.requireMasterOnRestart ? pinInfo.sessionRecord : pinLocal.record) &&
          pinLocal.failedAttempts < pinLocal.maxAttempts,
      );

      const matches: PasskeyMatchSummary[] = [];
      if (validOrigin && settings.passkeyAuthEnabled && account && secrets && cache) {
        const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
        const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
        const found = findMatchingPasskeys(
          decrypted.ciphers,
          request.rpId,
          request.allowCredentialIds,
        );
        for (const { cipher, credential } of found) {
          matches.push({
            cipherId: cipher.id!,
            cipherName: cipher.name,
            credentialId: credential.credentialId,
            rpId: credential.rpId,
            rpName: credential.rpName || cipher.name,
            userName: credential.userName || cipher.login?.username || "",
            userDisplayName: credential.userDisplayName || credential.userName || cipher.name,
          });
        }
      }

      const data: PasskeyPrepareGetResponse = {
        enabled: settings.passkeyAuthEnabled,
        validOrigin,
        hasAccount: Boolean(account),
        email: account?.email ?? null,
        unlocked: Boolean(account && secrets && cache),
        pinAvailableNow,
        pinLength: pinLocal?.pinLength ?? 6,
        matches,
      };
      return { ok: true, data };
    }

    case "PASSKEY_EXECUTE_GET": {
      if (!verifySenderPasskeyOrigin(request.rpId, request.origin, sender)) {
        return { ok: false, error: "Tên miền RP ID không hợp lệ cho trang web hiện tại." };
      }
      const { key, account } = await getUnlockedKey();
      const cache = (await getVaultCache()) ?? (await syncEncryptedVaultFromServer(true));
      const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);

      const cleanRpId = request.rpId.trim().toLowerCase().replace(/\.$/, "");
      const cipher = decrypted.ciphers.find(
        (c) => c.id === request.cipherId && !c.deletedDate && c.type === CipherType.Login,
      );
      const credential = cipher?.login?.fido2Credentials?.find(
        (f) =>
          f.credentialId === request.credentialId &&
          f.rpId.trim().toLowerCase().replace(/\.$/, "") === cleanRpId,
      );

      if (!credential) {
        return { ok: false, error: "Không tìm thấy mã khóa (Passkey) khớp trong kho." };
      }

      const clientDataJson = JSON.stringify({
        type: "webauthn.get",
        challenge: request.challengeB64Url,
        origin: request.origin,
        crossOrigin: false,
      });

      const signed = await signPasskeyAssertion(credential, utf8ToBytes(clientDataJson));
      const data: SerializedGetResponseData = {
        credentialIdB64Url: credential.credentialId,
        clientDataJsonB64Url: bytesToBase64Url(utf8ToBytes(clientDataJson)),
        authenticatorDataB64Url: bytesToBase64Url(signed.authenticatorData),
        signatureDerB64Url: bytesToBase64Url(signed.signatureDer),
        userHandleB64Url: credential.userHandle || null,
      };
      return { ok: true, data };
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
    await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 0.5 });
    const settings = await getSettings();
    if (settings.defaultBrowserPasswordManager) {
      await applyBrowserPasswordManagerPreference(true);
    }
  })();
});

chrome.runtime.onStartup.addListener(() => {
  void (async () => {
    await ensureSessionAccessPolicy();
    await setupContextMenus();
    await chrome.alarms.create(ALARMS.SYNC_POLL, { periodInMinutes: 0.5 });
    const settings = await getSettings();
    if (settings.defaultBrowserPasswordManager) {
      await applyBrowserPasswordManagerPreference(true);
    }
  })();
});

chrome.alarms.onAlarm.addListener((alarm) => {
  void (async () => {
    if (alarm.name === ALARMS.AUTO_LOCK) {
      await setSessionSecrets(null);
      await refreshActiveTabBadge();
    } else if (alarm.name === ALARMS.CLEAR_CLIPBOARD) {
      await writeClipboardViaOffscreen("");
    } else if (alarm.name === ALARMS.SYNC_POLL) {
      const secrets = await getSessionSecrets();
      if (secrets) {
        await syncEncryptedVaultFromServer(false).catch(() => undefined);
        await refreshActiveTabBadge();
      }
    }
  })();
});

// HTTP Basic Authentication Autofill (requires webRequest + webRequestAuthProvider)
if (chrome.webRequest?.onAuthRequired) {
  chrome.webRequest.onAuthRequired.addListener(
    (details, asyncCallback) => {
      const done = (response: chrome.webRequest.BlockingResponse) => {
        if (asyncCallback) asyncCallback(response);
      };

      void (async () => {
        try {
          if (details.isProxy) {
            done({});
            return;
          }
          const [account, secrets, settings, cache, triedRes] = await Promise.all([
            getAccount(),
            getSessionSecrets(),
            getSettings(),
            getVaultCache(),
            chrome.storage.session.get(SESSION_KEYS.BASIC_AUTH_ATTEMPTS),
          ]);

          if (!settings.autofillBasicAuth || !account || !secrets || !cache) {
            done({});
            return;
          }

          const triedList =
            (triedRes[SESSION_KEYS.BASIC_AUTH_ATTEMPTS] as string[] | undefined) ?? [];
          if (triedList.includes(details.requestId)) {
            // Already attempted once for this request; let Chrome prompt user instead of looping
            done({});
            return;
          }

          const key = splitSymmetricKey(base64ToBytes(secrets.userKeyB64));
          const decrypted = await decryptVaultCache(cache, key, account.lastSyncAt);
          const matches = findMatchingLogins(decrypted.ciphers, details.url);
          const top = matches.find((m) => m.username && m.password);

          if (!top) {
            done({});
            return;
          }

          const nextTried = [...triedList.slice(-24), details.requestId];
          await chrome.storage.session.set({ [SESSION_KEYS.BASIC_AUTH_ATTEMPTS]: nextTried });

          done({
            authCredentials: {
              username: top.username,
              password: top.password,
            },
          });
        } catch {
          done({});
        }
      })();
      return undefined;
    },
    { urls: ["http://*/*", "https://*/*"] },
    ["asyncBlocking"],
  );
}

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
        if (!filled) {
          await chrome.tabs.sendMessage(tab.id, {
            type: "OV_SHOW_TOAST",
            message: "Không tìm thấy tài khoản khớp cho trang này",
          });
        }
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
      await scheduleClipboardClear();
    }
  })();
});

chrome.runtime.onMessage.addListener((message: BgRequest, sender, sendResponse) => {
  if ((message as unknown as { target?: string })?.target === "offscreen") {
    return false;
  }
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
