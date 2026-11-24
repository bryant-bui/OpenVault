import {
  DEFAULT_SAFE_KDF_CONFIG,
  KdfType,
  MacVerificationError,
  SAFE_COOLDOWN_AFTER_FAILS,
  SAFE_COOLDOWN_MS,
  SAFE_DEFAULT_AUTO_LOCK_MINUTES,
  SAFE_MAX_FILE_BYTES,
  SAFE_MAX_TOTAL_FAILS,
  base64ToBytes,
  bytesToBase64,
  decryptSafeMetadata,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptSafeMetadata,
  generateFileKey,
  generateRecoveryKey,
  generateSafeKey,
  protectSafeKeyWithPin,
  protectSafeKeyWithRecoveryKey,
  splitSymmetricKey,
  unprotectSafeKeyWithPin,
  unprotectSafeKeyWithRecoveryKey,
  validateSafePin,
  zeroize,
  type SymmetricKey,
} from "@openvault/crypto-core";
import { ApiError } from "../shared/api.js";
import { ALARMS, LOCAL_KEYS, SESSION_KEYS } from "../shared/constants.js";
import {
  apiCreateSafeItem,
  apiGetSafeConfig,
  apiHardDeleteSafeItem,
  apiListSafeItems,
  apiPatchSafeSettings,
  apiReportSafeUnlockAttempt,
  apiResetSafe,
  apiRestoreSafeItem,
  apiSetupSafe,
  apiSoftDeleteSafeItem,
  apiUpdateSafeItem,
  apiUpdateSafePasscode,
} from "../shared/safe-api.js";
import type {
  DecryptedSafeItem,
  SafeBlobAuthResult,
  SafeConfigDto,
  SafeLockoutState,
  SafePrepareUploadResult,
  SafeSessionSecrets,
  SafeStatusResponse,
} from "../shared/safe-types.js";
import type { BgRequest, BgResponse, StoredAccount } from "../shared/types.js";

export type SafeBgRequest = Extract<
  BgRequest,
  {
    type:
      | "SAFE_GET_STATUS"
      | "SAFE_SETUP"
      | "SAFE_UNLOCK_PIN"
      | "SAFE_RECOVER"
      | "SAFE_CHANGE_PIN"
      | "SAFE_UPDATE_SETTINGS"
      | "SAFE_LOCK"
      | "SAFE_LIST_ITEMS"
      | "SAFE_PREPARE_UPLOAD"
      | "SAFE_GET_AUTH_FOR_BLOB"
      | "SAFE_UPDATE_ITEM"
      | "SAFE_DELETE_ITEM"
      | "SAFE_RESTORE_ITEM"
      | "SAFE_RESET";
  }
>;

export interface SafeHostDeps {
  getAccount: () => Promise<StoredAccount | null>;
  withValidAccessToken: <T>(
    op: (serverUrl: string, accessToken: string) => Promise<T>,
  ) => Promise<T>;
}

export async function getSafeSessionSecrets(): Promise<SafeSessionSecrets | null> {
  const res = await chrome.storage.session.get(SESSION_KEYS.SAFE_SECRETS);
  const secrets = (res[SESSION_KEYS.SAFE_SECRETS] as SafeSessionSecrets | undefined) ?? null;
  if (!secrets) return null;
  if (Date.now() >= secrets.expiresAtMs) {
    await clearSafeSession();
    return null;
  }
  return secrets;
}

export async function setSafeSessionSecrets(secrets: SafeSessionSecrets | null): Promise<void> {
  if (!secrets) {
    await chrome.storage.session.remove(SESSION_KEYS.SAFE_SECRETS);
    return;
  }
  await chrome.storage.session.set({ [SESSION_KEYS.SAFE_SECRETS]: secrets });
}

export async function clearSafeSession(): Promise<void> {
  await Promise.all([
    chrome.storage.session.remove(SESSION_KEYS.SAFE_SECRETS),
    chrome.alarms.clear(ALARMS.SAFE_AUTO_LOCK),
  ]);
}

export async function clearSafeAllLocal(): Promise<void> {
  await Promise.all([
    clearSafeSession(),
    chrome.storage.local.remove(LOCAL_KEYS.SAFE_LOCKOUT),
  ]);
}

async function getLocalLockout(): Promise<SafeLockoutState> {
  const res = await chrome.storage.local.get(LOCAL_KEYS.SAFE_LOCKOUT);
  return (
    (res[LOCAL_KEYS.SAFE_LOCKOUT] as SafeLockoutState | undefined) ?? {
      failedAttempts: 0,
      lockedUntilMs: null,
      recoveryRequired: false,
      cachedRevisionDate: null,
      cachedConfig: null,
    }
  );
}

async function setLocalLockout(state: SafeLockoutState): Promise<void> {
  await chrome.storage.local.set({ [LOCAL_KEYS.SAFE_LOCKOUT]: state });
}

async function scheduleSafeAutoLock(autoLockMinutes: number): Promise<number> {
  const clamped = Math.min(15, Math.max(1, Math.round(autoLockMinutes)));
  const expiresAtMs = Date.now() + clamped * 60_000;
  await chrome.alarms.clear(ALARMS.SAFE_AUTO_LOCK);
  await chrome.alarms.create(ALARMS.SAFE_AUTO_LOCK, {
    delayInMinutes: Math.max(0.5, clamped),
  });
  return expiresAtMs;
}

async function touchSafeSession(): Promise<{ key: SymmetricKey; secrets: SafeSessionSecrets }> {
  const secrets = await getSafeSessionSecrets();
  if (!secrets) {
    throw new Error("Vault+ đang khóa. Vui lòng nhập mã PIN 6 số.");
  }
  const expiresAtMs = await scheduleSafeAutoLock(secrets.autoLockMinutes);
  const updated: SafeSessionSecrets = { ...secrets, expiresAtMs };
  await setSafeSessionSecrets(updated);
  return {
    key: splitSymmetricKey(base64ToBytes(secrets.safeKeyB64)),
    secrets: updated,
  };
}

function mergeConfigWithLocal(
  serverCfg: SafeConfigDto,
  local: SafeLockoutState,
): SafeLockoutState {
  const now = Date.now();
  if (!serverCfg.initialized) {
    return {
      failedAttempts: 0,
      lockedUntilMs: null,
      recoveryRequired: false,
      cachedRevisionDate: null,
      cachedConfig: serverCfg,
    };
  }

  const serverRecoveredOrReset =
    Boolean(local.cachedRevisionDate) &&
    serverCfg.revisionDate !== local.cachedRevisionDate &&
    !serverCfg.recoveryRequired &&
    (serverCfg.failedAttempts ?? 0) === 0 &&
    !serverCfg.lockedUntilMs;

  const failedAttempts = serverRecoveredOrReset
    ? 0
    : Math.max(serverCfg.failedAttempts ?? 0, local.failedAttempts);
  const rawLockedUntil = serverRecoveredOrReset
    ? null
    : Math.max(serverCfg.lockedUntilMs ?? 0, local.lockedUntilMs ?? 0) || null;
  const lockedUntilMs = rawLockedUntil && rawLockedUntil > now ? rawLockedUntil : null;
  const recoveryRequired = serverRecoveredOrReset
    ? false
    : Boolean(serverCfg.recoveryRequired) || local.recoveryRequired;

  return {
    failedAttempts,
    lockedUntilMs,
    recoveryRequired,
    cachedRevisionDate: serverCfg.revisionDate ?? null,
    cachedConfig: serverCfg,
  };
}

function buildStatusResponse(
  merged: SafeLockoutState,
  secrets: SafeSessionSecrets | null,
): SafeStatusResponse {
  const cfg = merged.cachedConfig;
  const initialized = Boolean(cfg?.initialized);
  const now = Date.now();
  const lockedUntil = merged.lockedUntilMs && merged.lockedUntilMs > now ? merged.lockedUntilMs : null;
  const unlocked =
    initialized &&
    Boolean(secrets) &&
    !merged.recoveryRequired &&
    lockedUntil === null;

  return {
    initialized,
    unlocked,
    autoLockMinutes: cfg?.autoLockMinutes ?? secrets?.autoLockMinutes ?? SAFE_DEFAULT_AUTO_LOCK_MINUTES,
    unlockedExpiresAtMs: unlocked ? (secrets?.expiresAtMs ?? null) : null,
    failedAttempts: merged.failedAttempts,
    lockedUntilMs: lockedUntil,
    recoveryRequired: merged.recoveryRequired,
    usedBytes: cfg?.usedBytes ?? 0,
    itemCount: cfg?.itemCount ?? 0,
    maxFileBytes: cfg?.maxFileBytes ?? SAFE_MAX_FILE_BYTES,
    hasRecoveryConfig: Boolean(cfg?.recoveryEncryptedSafeKey),
  };
}

async function fetchAndMergeConfig(
  deps: SafeHostDeps,
  forceSync: boolean,
): Promise<SafeLockoutState> {
  const local = await getLocalLockout();
  if (!forceSync && local.cachedConfig) {
    return local;
  }
  const serverCfg = await deps.withValidAccessToken((url, token) =>
    apiGetSafeConfig(url, token),
  );
  const merged = mergeConfigWithLocal(serverCfg, local);
  await setLocalLockout(merged);
  if (!serverCfg.initialized || merged.recoveryRequired || merged.lockedUntilMs) {
    await clearSafeSession();
  }
  return merged;
}

async function handlePinFailure(
  deps: SafeHostDeps,
  currentLockout: SafeLockoutState,
): Promise<{ message: string; lockout: SafeLockoutState }> {
  let nextFailed = currentLockout.failedAttempts + 1;
  let nextRecovery = nextFailed >= SAFE_MAX_TOTAL_FAILS;
  let nextLockedUntil: number | null =
    !nextRecovery && nextFailed % SAFE_COOLDOWN_AFTER_FAILS === 0
      ? Date.now() + SAFE_COOLDOWN_MS
      : null;

  try {
    const remote = await deps.withValidAccessToken((url, token) =>
      apiReportSafeUnlockAttempt(url, token, false),
    );
    nextFailed = Math.max(nextFailed, remote.failedAttempts);
    nextRecovery = nextRecovery || remote.recoveryRequired;
    nextLockedUntil =
      Math.max(nextLockedUntil ?? 0, remote.lockedUntilMs ?? 0) || null;
  } catch {
    // Keep local lockout state if offline
  }

  if (nextRecovery) {
    nextLockedUntil = null;
    await clearSafeSession();
  }

  const nextState: SafeLockoutState = {
    ...currentLockout,
    failedAttempts: nextFailed,
    lockedUntilMs: nextLockedUntil,
    recoveryRequired: nextRecovery,
  };
  await setLocalLockout(nextState);

  const message = nextRecovery
    ? "Đã nhập sai 15 lần. Bắt buộc dùng Recovery Key hoặc xóa toàn bộ Vault+."
    : nextLockedUntil
      ? "Nhập sai 5 lần. Đã tạm khóa 15 phút."
      : `Mã PIN Vault+ không đúng (đã sai ${nextFailed}/${SAFE_MAX_TOTAL_FAILS} lần).`;
  return { message, lockout: nextState };
}

export async function handleSafeMessage(
  request: SafeBgRequest,
  deps: SafeHostDeps,
): Promise<BgResponse> {
  switch (request.type) {
    case "SAFE_GET_STATUS": {
      const account = await deps.getAccount();
      if (!account) {
        return {
          ok: true,
          data: buildStatusResponse(
            { failedAttempts: 0, lockedUntilMs: null, recoveryRequired: false, cachedConfig: null },
            null,
          ),
        };
      }
      let merged = await getLocalLockout();
      try {
        merged = await fetchAndMergeConfig(deps, request.forceSync ?? true);
      } catch {
        // Offline fallback uses cached config & lockout
      }
      const secrets = await getSafeSessionSecrets();
      return { ok: true, data: buildStatusResponse(merged, secrets) };
    }

    case "SAFE_SETUP": {
      validateSafePin(request.pin);
      const safeKey = generateSafeKey();
      const recovery = generateRecoveryKey();
      try {
        const pinWrapped = await protectSafeKeyWithPin(safeKey, request.pin, DEFAULT_SAFE_KDF_CONFIG);
        const recWrapped = await protectSafeKeyWithRecoveryKey(safeKey, recovery.formatted);
        const created = await deps.withValidAccessToken((url, token) =>
          apiSetupSafe(url, token, {
            kdfType: pinWrapped.kdfConfig.kdf,
            kdfIterations: pinWrapped.kdfConfig.kdfIterations,
            kdfMemory: pinWrapped.kdfConfig.kdfMemory,
            kdfParallelism: pinWrapped.kdfConfig.kdfParallelism,
            passcodeSalt: pinWrapped.saltB64,
            passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
            recoverySalt: recWrapped.saltB64,
            recoveryKdfIterations: recWrapped.iterations,
            recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
            autoLockMinutes: SAFE_DEFAULT_AUTO_LOCK_MINUTES,
          }),
        );

        const autoLock = created.autoLockMinutes ?? SAFE_DEFAULT_AUTO_LOCK_MINUTES;
        const expiresAtMs = await scheduleSafeAutoLock(autoLock);
        const secrets: SafeSessionSecrets = {
          safeKeyB64: bytesToBase64(safeKey.raw),
          unlockedAtMs: Date.now(),
          autoLockMinutes: autoLock,
          expiresAtMs,
        };
        await setSafeSessionSecrets(secrets);
        const merged: SafeLockoutState = {
          failedAttempts: 0,
          lockedUntilMs: null,
          recoveryRequired: false,
          cachedRevisionDate: created.revisionDate ?? null,
          cachedConfig: created,
        };
        await setLocalLockout(merged);

        return {
          ok: true,
          data: {
            recoveryKey: recovery.formatted,
            status: buildStatusResponse(merged, secrets),
          },
        };
      } finally {
        zeroize(safeKey.encKey, safeKey.macKey, safeKey.raw, recovery.rawBytes);
      }
    }

    case "SAFE_UNLOCK_PIN": {
      validateSafePin(request.pin);
      let lockout = await fetchAndMergeConfig(deps, false);
      const now = Date.now();

      if (lockout.recoveryRequired) {
        return {
          ok: false,
          error: "Đã nhập sai quá 15 lần. Bắt buộc dùng Recovery Key hoặc xóa dữ liệu Vault+.",
        };
      }
      if (lockout.lockedUntilMs && lockout.lockedUntilMs > now) {
        return { ok: false, error: "Đang trong thời gian tạm khóa 15 phút." };
      }

      if (
        !lockout.cachedConfig?.passcodeEncryptedSafeKey ||
        !lockout.cachedConfig?.passcodeSalt
      ) {
        lockout = await fetchAndMergeConfig(deps, true);
        if (lockout.recoveryRequired) {
          return { ok: false, error: "Đã nhập sai quá 15 lần. Cần Recovery Key." };
        }
        if (lockout.lockedUntilMs && lockout.lockedUntilMs > Date.now()) {
          return { ok: false, error: "Đang trong thời gian tạm khóa 15 phút." };
        }
      }

      const cfg = lockout.cachedConfig;
      if (!cfg?.initialized || !cfg.passcodeEncryptedSafeKey || !cfg.passcodeSalt) {
        return { ok: false, error: "Vault+ chưa sẵn sàng hoặc đang bị khóa tạm thời." };
      }

      const kdfConfig = {
        kdf: cfg.kdfType ?? KdfType.Argon2id,
        kdfIterations: cfg.kdfIterations ?? 3,
        kdfMemory: cfg.kdfMemory ?? 64,
        kdfParallelism: cfg.kdfParallelism ?? 4,
      };

      let safeKey: SymmetricKey;
      try {
        safeKey = await unprotectSafeKeyWithPin(
          cfg.passcodeEncryptedSafeKey,
          request.pin,
          cfg.passcodeSalt,
          kdfConfig,
        );
      } catch (err) {
        if (err instanceof MacVerificationError) {
          const failure = await handlePinFailure(deps, lockout);
          return { ok: false, error: failure.message };
        }
        throw err;
      }

      try {
        try {
          await deps.withValidAccessToken((url, token) =>
            apiReportSafeUnlockAttempt(url, token, true),
          );
        } catch (err) {
          if (err instanceof ApiError && (err.status === 423 || err.status === 429)) {
            await fetchAndMergeConfig(deps, true);
            return { ok: false, error: err.message };
          }
        }

        const autoLock = cfg.autoLockMinutes ?? SAFE_DEFAULT_AUTO_LOCK_MINUTES;
        const expiresAtMs = await scheduleSafeAutoLock(autoLock);
        const secrets: SafeSessionSecrets = {
          safeKeyB64: bytesToBase64(safeKey.raw),
          unlockedAtMs: Date.now(),
          autoLockMinutes: autoLock,
          expiresAtMs,
        };
        await setSafeSessionSecrets(secrets);

        const nextLockout: SafeLockoutState = {
          ...lockout,
          failedAttempts: 0,
          lockedUntilMs: null,
          recoveryRequired: false,
        };
        await setLocalLockout(nextLockout);
        return { ok: true, data: buildStatusResponse(nextLockout, secrets) };
      } finally {
        zeroize(safeKey.encKey, safeKey.macKey, safeKey.raw);
      }
    }

    case "SAFE_RECOVER": {
      validateSafePin(request.newPin);
      const lockout = await fetchAndMergeConfig(deps, true);
      const cfg = lockout.cachedConfig;
      if (!cfg?.initialized || !cfg.recoveryEncryptedSafeKey || !cfg.recoverySalt) {
        return { ok: false, error: "Không tìm thấy thông tin khôi phục Vault+." };
      }

      let safeKey: SymmetricKey;
      try {
        safeKey = await unprotectSafeKeyWithRecoveryKey(
          cfg.recoveryEncryptedSafeKey,
          request.recoveryKey,
          cfg.recoverySalt,
          cfg.recoveryKdfIterations ?? 100_000,
        );
      } catch {
        return { ok: false, error: "Mã Recovery Key không chính xác." };
      }

      const rotated = request.rotateRecoveryKey ? generateRecoveryKey() : null;
      try {
        const pinWrapped = await protectSafeKeyWithPin(safeKey, request.newPin, DEFAULT_SAFE_KDF_CONFIG);
        const recWrapped = rotated
          ? await protectSafeKeyWithRecoveryKey(safeKey, rotated.formatted)
          : null;

        const updatedCfg = await deps.withValidAccessToken((url, token) =>
          apiUpdateSafePasscode(url, token, {
            mode: "recovery",
            kdfType: pinWrapped.kdfConfig.kdf,
            kdfIterations: pinWrapped.kdfConfig.kdfIterations,
            kdfMemory: pinWrapped.kdfConfig.kdfMemory,
            kdfParallelism: pinWrapped.kdfConfig.kdfParallelism,
            passcodeSalt: pinWrapped.saltB64,
            passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
            ...(recWrapped
              ? {
                  recoverySalt: recWrapped.saltB64,
                  recoveryKdfIterations: recWrapped.iterations,
                  recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
                }
              : {}),
          }),
        );

        const autoLock = updatedCfg.autoLockMinutes ?? SAFE_DEFAULT_AUTO_LOCK_MINUTES;
        const expiresAtMs = await scheduleSafeAutoLock(autoLock);
        const secrets: SafeSessionSecrets = {
          safeKeyB64: bytesToBase64(safeKey.raw),
          unlockedAtMs: Date.now(),
          autoLockMinutes: autoLock,
          expiresAtMs,
        };
        await setSafeSessionSecrets(secrets);

        const nextLockout: SafeLockoutState = {
          failedAttempts: 0,
          lockedUntilMs: null,
          recoveryRequired: false,
          cachedRevisionDate: updatedCfg.revisionDate ?? null,
          cachedConfig: updatedCfg,
        };
        await setLocalLockout(nextLockout);

        return {
          ok: true,
          data: {
            recoveryKey: rotated?.formatted ?? null,
            status: buildStatusResponse(nextLockout, secrets),
          },
        };
      } finally {
        zeroize(safeKey.encKey, safeKey.macKey, safeKey.raw, rotated?.rawBytes);
      }
    }

    case "SAFE_CHANGE_PIN": {
      validateSafePin(request.currentPin);
      validateSafePin(request.newPin);
      const { key: activeSafeKey } = await touchSafeSession();
      const lockout = await fetchAndMergeConfig(deps, true);
      const cfg = lockout.cachedConfig;
      if (!cfg?.passcodeEncryptedSafeKey || !cfg.passcodeSalt) {
        return { ok: false, error: "Không thể xác minh mã PIN hiện tại." };
      }

      try {
        const verified = await unprotectSafeKeyWithPin(
          cfg.passcodeEncryptedSafeKey,
          request.currentPin,
          cfg.passcodeSalt,
          {
            kdf: cfg.kdfType ?? KdfType.Argon2id,
            kdfIterations: cfg.kdfIterations ?? 3,
            kdfMemory: cfg.kdfMemory ?? 64,
            kdfParallelism: cfg.kdfParallelism ?? 4,
          },
        );
        zeroize(verified.encKey, verified.macKey, verified.raw);
      } catch (err) {
        if (err instanceof MacVerificationError) {
          const failure = await handlePinFailure(deps, lockout);
          return { ok: false, error: failure.message };
        }
        throw err;
      }

      try {
        const wrapped = await protectSafeKeyWithPin(activeSafeKey, request.newPin, DEFAULT_SAFE_KDF_CONFIG);
        const updatedCfg = await deps.withValidAccessToken((url, token) =>
          apiUpdateSafePasscode(url, token, {
            mode: "change",
            kdfType: wrapped.kdfConfig.kdf,
            kdfIterations: wrapped.kdfConfig.kdfIterations,
            kdfMemory: wrapped.kdfConfig.kdfMemory,
            kdfParallelism: wrapped.kdfConfig.kdfParallelism,
            passcodeSalt: wrapped.saltB64,
            passcodeEncryptedSafeKey: wrapped.encryptedSafeKey,
          }),
        );
        await setLocalLockout({
          failedAttempts: 0,
          lockedUntilMs: null,
          recoveryRequired: false,
          cachedRevisionDate: updatedCfg.revisionDate ?? null,
          cachedConfig: updatedCfg,
        });
        return { ok: true, data: { updated: true } };
      } finally {
        zeroize(activeSafeKey.encKey, activeSafeKey.macKey, activeSafeKey.raw);
      }
    }

    case "SAFE_UPDATE_SETTINGS": {
      const { key, secrets } = await touchSafeSession();
      zeroize(key.encKey, key.macKey, key.raw);
      const res = await deps.withValidAccessToken((url, token) =>
        apiPatchSafeSettings(url, token, request.autoLockMinutes),
      );
      const expiresAtMs = await scheduleSafeAutoLock(res.autoLockMinutes);
      await setSafeSessionSecrets({
        ...secrets,
        autoLockMinutes: res.autoLockMinutes,
        expiresAtMs,
      });
      const local = await getLocalLockout();
      if (local.cachedConfig) {
        await setLocalLockout({
          ...local,
          cachedConfig: {
            ...local.cachedConfig,
            autoLockMinutes: res.autoLockMinutes,
          },
        });
      }
      return { ok: true, data: { autoLockMinutes: res.autoLockMinutes } };
    }

    case "SAFE_LOCK": {
      await clearSafeSession();
      return { ok: true, data: { locked: true } };
    }

    case "SAFE_LIST_ITEMS": {
      const { key } = await touchSafeSession();
      try {
        const rawItems = await deps.withValidAccessToken((url, token) =>
          apiListSafeItems(url, token),
        );
        const decrypted: DecryptedSafeItem[] = [];
        for (const item of rawItems) {
          try {
            const metadata = await decryptSafeMetadata(item.encryptedMetadata, key);
            decrypted.push({
              id: item.id,
              category: metadata.category ?? item.category,
              metadata,
              hasThumbnail: item.hasThumbnail,
              blobSizeBytes: item.blobSizeBytes,
              thumbSizeBytes: item.thumbSizeBytes,
              blobSha256: item.blobSha256,
              favorite: item.favorite,
              deletedAt: item.deletedAt,
              revisionDate: item.revisionDate,
              createdAt: item.createdAt,
              updatedAt: item.updatedAt,
            });
          } catch {
            // Skip unreadable entry cleanly
          }
        }
        return { ok: true, data: decrypted };
      } finally {
        zeroize(key.encKey, key.macKey, key.raw);
      }
    }

    case "SAFE_PREPARE_UPLOAD": {
      const { key } = await touchSafeSession();
      const fileKey = generateFileKey();
      const thumbKey = request.hasThumb ? generateFileKey() : null;
      const fileKeyB64 = bytesToBase64(fileKey);
      const thumbKeyB64 = thumbKey ? bytesToBase64(thumbKey) : null;
      zeroize(fileKey, thumbKey);

      try {
        const encryptedMetadata = await encryptSafeMetadata(
          {
            v: 1,
            title: request.title.trim() || request.fileName,
            fileName: request.fileName,
            mimeType: request.mimeType,
            category: request.category,
            notes: request.notes ?? null,
            fileKeyB64,
            thumbKeyB64,
            originalSize: request.originalSize,
            originalSha256: request.originalSha256,
            expiresAt: request.expiresAt ?? null,
          },
          key,
        );

        let resultPayload: SafePrepareUploadResult | null = null;
        await deps.withValidAccessToken(async (serverUrl, accessToken) => {
          const created = await apiCreateSafeItem(serverUrl, accessToken, {
            category: request.category,
            encryptedMetadata,
            favorite: false,
          });
          resultPayload = {
            serverUrl,
            accessToken,
            itemId: created.id,
            fileKeyB64,
            thumbKeyB64,
          };
        });
        return { ok: true, data: resultPayload! };
      } finally {
        zeroize(key.encKey, key.macKey, key.raw);
      }
    }

    case "SAFE_GET_AUTH_FOR_BLOB": {
      const { key } = await touchSafeSession();
      zeroize(key.encKey, key.macKey, key.raw);
      let auth: SafeBlobAuthResult | null = null;
      await deps.withValidAccessToken(async (serverUrl, accessToken) => {
        auth = { serverUrl, accessToken };
      });
      return { ok: true, data: auth! };
    }

    case "SAFE_UPDATE_ITEM": {
      const { key } = await touchSafeSession();
      try {
        const items = await deps.withValidAccessToken((url, token) =>
          apiListSafeItems(url, token),
        );
        const target = items.find((i) => i.id === request.id);
        if (!target) {
          return { ok: false, error: "Không tìm thấy tài liệu." };
        }
        const existingMeta = await decryptSafeMetadata(target.encryptedMetadata, key);
        const encryptedMetadata = await encryptSafeMetadata(
          {
            ...existingMeta,
            title: request.title.trim() || existingMeta.fileName,
            category: request.category,
            notes: request.notes ?? null,
            expiresAt: request.expiresAt ?? null,
          },
          key,
        );
        const updated = await deps.withValidAccessToken((url, token) =>
          apiUpdateSafeItem(url, token, request.id, {
            category: request.category,
            encryptedMetadata,
            favorite: request.favorite,
          }),
        );
        return { ok: true, data: updated };
      } finally {
        zeroize(key.encKey, key.macKey, key.raw);
      }
    }

    case "SAFE_DELETE_ITEM": {
      const { key } = await touchSafeSession();
      zeroize(key.encKey, key.macKey, key.raw);
      if (request.permanent) {
        await deps.withValidAccessToken((url, token) =>
          apiHardDeleteSafeItem(url, token, request.id),
        );
      } else {
        await deps.withValidAccessToken((url, token) =>
          apiSoftDeleteSafeItem(url, token, request.id),
        );
      }
      return { ok: true, data: { deleted: true } };
    }

    case "SAFE_RESTORE_ITEM": {
      const { key } = await touchSafeSession();
      zeroize(key.encKey, key.macKey, key.raw);
      const restored = await deps.withValidAccessToken((url, token) =>
        apiRestoreSafeItem(url, token, request.id),
      );
      return { ok: true, data: restored };
    }

    case "SAFE_RESET": {
      const account = await deps.getAccount();
      if (!account) return { ok: false, error: "Chưa đăng nhập." };
      const masterKey = await deriveMasterKey(
        request.masterPassword,
        account.email,
        account.kdfConfig,
      );
      let masterPasswordHash: string;
      try {
        masterPasswordHash = await deriveMasterPasswordHash(masterKey, request.masterPassword, 1);
      } finally {
        zeroize(masterKey);
      }
      await deps.withValidAccessToken((url, token) =>
        apiResetSafe(url, token, masterPasswordHash),
      );
      await clearSafeAllLocal();
      return { ok: true, data: { reset: true } };
    }
  }
}
