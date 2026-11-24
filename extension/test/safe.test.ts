import { beforeEach, describe, expect, it, vi } from "vitest";
import { getStrings } from "../src/shared/i18n.js";
import type { SafeConfigDto, SafeStatusResponse } from "../src/shared/safe-types.js";

function createMockStorageArea() {
  let store: Record<string, unknown> = {};
  return {
    get: vi.fn(async (keys?: string | string[] | null) => {
      if (!keys) return { ...store };
      const list = Array.isArray(keys) ? keys : [keys];
      const out: Record<string, unknown> = {};
      for (const k of list) {
        if (k in store) out[k] = store[k];
      }
      return out;
    }),
    set: vi.fn(async (items: Record<string, unknown>) => {
      store = { ...store, ...items };
    }),
    remove: vi.fn(async (keys: string | string[]) => {
      const list = Array.isArray(keys) ? keys : [keys];
      for (const k of list) delete store[k];
    }),
  };
}

describe("OpenVault Extension — Vault+ Service Worker Handler & i18n", () => {
  let localArea: ReturnType<typeof createMockStorageArea>;
  let sessionArea: ReturnType<typeof createMockStorageArea>;
  let serverConfig: SafeConfigDto | null;
  let remoteLockedUntil: number | null;
  let remoteFailedAttempts: number;
  let remoteRecoveryRequired: boolean;

  beforeEach(() => {
    vi.restoreAllMocks();
    localArea = createMockStorageArea();
    sessionArea = createMockStorageArea();
    serverConfig = null;
    remoteLockedUntil = null;
    remoteFailedAttempts = 0;
    remoteRecoveryRequired = false;

    vi.stubGlobal("chrome", {
      storage: {
        local: localArea,
        session: sessionArea,
      },
      alarms: {
        clear: vi.fn(async () => true),
        create: vi.fn(async () => undefined),
      },
    });

    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init?: RequestInit) => {
        const path = new URL(url).pathname;
        if (path === "/api/openvault/safe/config") {
          if (!serverConfig) {
            return new Response(JSON.stringify({ initialized: false, maxFileBytes: 5242880 }), {
              status: 200,
            });
          }
          const now = Date.now();
          const cooling = Boolean(remoteLockedUntil && remoteLockedUntil > now);
          const withhold = cooling || remoteRecoveryRequired;
          return new Response(
            JSON.stringify({
              ...serverConfig,
              passcodeSalt: withhold ? null : serverConfig.passcodeSalt,
              passcodeEncryptedSafeKey: withhold ? null : serverConfig.passcodeEncryptedSafeKey,
              failedAttempts: remoteFailedAttempts,
              lockedUntilMs: cooling ? remoteLockedUntil : null,
              recoveryRequired: remoteRecoveryRequired,
            }),
            { status: 200 },
          );
        }

        if (path === "/api/openvault/safe/setup") {
          const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
          serverConfig = {
            initialized: true,
            version: 1,
            kdfType: body.kdfType as number,
            kdfIterations: body.kdfIterations as number,
            kdfMemory: body.kdfMemory as number | null,
            kdfParallelism: body.kdfParallelism as number | null,
            passcodeSalt: body.passcodeSalt as string,
            passcodeEncryptedSafeKey: body.passcodeEncryptedSafeKey as string,
            recoverySalt: body.recoverySalt as string,
            recoveryKdfIterations: body.recoveryKdfIterations as number,
            recoveryEncryptedSafeKey: body.recoveryEncryptedSafeKey as string,
            autoLockMinutes: 3,
            failedAttempts: 0,
            lockedUntilMs: null,
            recoveryRequired: false,
            usedBytes: 0,
            itemCount: 0,
            maxFileBytes: 5242880,
            revisionDate: new Date().toISOString(),
          };
          return new Response(JSON.stringify(serverConfig), { status: 201 });
        }

        if (path === "/api/openvault/safe/unlock-attempt") {
          const body = JSON.parse(String(init?.body)) as { success: boolean };
          if (body.success) {
            remoteFailedAttempts = 0;
            remoteLockedUntil = null;
            return new Response(
              JSON.stringify({
                ok: true,
                failedAttempts: 0,
                lockedUntilMs: null,
                recoveryRequired: false,
              }),
              { status: 200 },
            );
          }
          remoteFailedAttempts += 1;
          if (remoteFailedAttempts >= 15) {
            remoteRecoveryRequired = true;
            remoteLockedUntil = null;
          } else if (remoteFailedAttempts % 5 === 0) {
            remoteLockedUntil = Date.now() + 15 * 60_000;
          }
          return new Response(
            JSON.stringify({
              ok: false,
              failedAttempts: remoteFailedAttempts,
              lockedUntilMs: remoteLockedUntil,
              recoveryRequired: remoteRecoveryRequired,
            }),
            { status: 200 },
          );
        }

        if (path === "/api/openvault/safe/passcode") {
          const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
          remoteFailedAttempts = 0;
          remoteLockedUntil = null;
          remoteRecoveryRequired = false;
          serverConfig = {
            ...serverConfig!,
            passcodeSalt: body.passcodeSalt as string,
            passcodeEncryptedSafeKey: body.passcodeEncryptedSafeKey as string,
            failedAttempts: 0,
            lockedUntilMs: null,
            recoveryRequired: false,
            revisionDate: new Date().toISOString(),
          };
          return new Response(JSON.stringify(serverConfig), { status: 200 });
        }

        return new Response(JSON.stringify({ error: "not_found" }), { status: 404 });
      }),
    );
  });

  it("provides full EN/VI i18n strings for Vault+", () => {
    const en = getStrings("en");
    const vi = getStrings("vi");
    expect(en.vaultPlus).toBe("Vault+");
    expect(vi.vaultPlus).toBe("Vault+");
    expect(en.vaultPlusRecoveryRequiredTitle.length).toBeGreaterThan(5);
    expect(vi.vaultPlusRecoveryRequiredTitle.length).toBeGreaterThan(5);
  });

  it("handles setup, lock, PIN unlock, 5-fail cooldown, 15-fail recovery lockout, and Recovery Key reset", async () => {
    const { handleSafeMessage } = await import("../src/background/safe-handler.js");
    const deps = {
      getAccount: async () => ({
        serverUrl: "https://vault.openvault.test",
        email: "user@openvault.test",
        name: "Owner",
        kdfConfig: { kdf: 1, kdfIterations: 3, kdfMemory: 64, kdfParallelism: 4 },
        protectedKey: "2.a|b|c",
        encryptedPrivateKey: null,
        refreshToken: "rt",
        lastRevisionMs: 1,
        lastSyncAt: null,
      }),
      withValidAccessToken: async <T>(op: (url: string, tok: string) => Promise<T>) =>
        op("https://vault.openvault.test", "jwt-token"),
    };

    const setupRes = await handleSafeMessage({ type: "SAFE_SETUP", pin: "246810" }, deps);
    expect(setupRes.ok).toBe(true);
    const { recoveryKey } = (setupRes as { ok: true; data: { recoveryKey: string } }).data;
    expect(recoveryKey.startsWith("OVRK-")).toBe(true);

    // Lock clears session RAM
    await handleSafeMessage({ type: "SAFE_LOCK" }, deps);
    const lockedStatus = await handleSafeMessage({ type: "SAFE_GET_STATUS", forceSync: true }, deps);
    expect((lockedStatus as { ok: true; data: SafeStatusResponse }).data.unlocked).toBe(false);

    // Valid 6-digit PIN unlocks
    const okUnlock = await handleSafeMessage({ type: "SAFE_UNLOCK_PIN", pin: "246810" }, deps);
    expect(okUnlock.ok).toBe(true);
    await handleSafeMessage({ type: "SAFE_LOCK" }, deps);

    // 5 failed PIN attempts -> 15-minute cooldown
    for (let i = 1; i <= 5; i++) {
      const fail = await handleSafeMessage({ type: "SAFE_UNLOCK_PIN", pin: "000000" }, deps);
      expect(fail.ok).toBe(false);
    }
    const coolingStatus = (
      await handleSafeMessage({ type: "SAFE_GET_STATUS", forceSync: true }, deps)
    ) as { ok: true; data: SafeStatusResponse };
    expect(coolingStatus.data.failedAttempts).toBe(5);
    expect(coolingStatus.data.lockedUntilMs).toBeGreaterThan(Date.now());

    // Blocked even with correct PIN during cooldown
    const blocked = await handleSafeMessage({ type: "SAFE_UNLOCK_PIN", pin: "246810" }, deps);
    expect(blocked.ok).toBe(false);

    // Simulate cooldown expiry twice until 15th failure triggers permanent recoveryRequired
    for (const batchStart of [6, 11]) {
      remoteLockedUntil = Date.now() - 1000;
      const localRes = await localArea.get("ov_safe_lockout");
      await localArea.set({
        ov_safe_lockout: { ...(localRes.ov_safe_lockout as object), lockedUntilMs: null },
      });
      await handleSafeMessage({ type: "SAFE_GET_STATUS", forceSync: true }, deps);
      for (let i = batchStart; i < batchStart + 5; i++) {
        const fail = await handleSafeMessage({ type: "SAFE_UNLOCK_PIN", pin: "000000" }, deps);
        expect(fail.ok).toBe(false);
      }
    }

    const recoveryStatus = (
      await handleSafeMessage({ type: "SAFE_GET_STATUS", forceSync: true }, deps)
    ) as { ok: true; data: SafeStatusResponse };
    expect(recoveryStatus.data.failedAttempts).toBe(15);
    expect(recoveryStatus.data.recoveryRequired).toBe(true);

    // Recover using standalone Recovery Key & set new 6-digit PIN
    const recoverRes = await handleSafeMessage(
      { type: "SAFE_RECOVER", recoveryKey, newPin: "987654", rotateRecoveryKey: false },
      deps,
    );
    expect(recoverRes.ok).toBe(true);

    // Lock and unlock cleanly with new PIN
    await handleSafeMessage({ type: "SAFE_LOCK" }, deps);
    const unlockNewPin = await handleSafeMessage({ type: "SAFE_UNLOCK_PIN", pin: "987654" }, deps);
    expect(unlockNewPin.ok).toBe(true);

    // Folder CRUD & deduplication in SAFE_* messages
    const f1 = await handleSafeMessage({ type: "SAFE_CREATE_FOLDER", name: "Passports" }, deps);
    expect(f1.ok).toBe(true);
    expect((f1 as { ok: true; data: string[] }).data).toEqual(["Passports"]);

    const f2 = await handleSafeMessage({ type: "SAFE_CREATE_FOLDER", name: "  passports  " }, deps);
    expect(f2.ok).toBe(true);
    expect((f2 as { ok: true; data: string[] }).data).toEqual(["Passports"]);

    const listed = await handleSafeMessage({ type: "SAFE_LIST_FOLDERS" }, deps);
    expect((listed as { ok: true; data: string[] }).data).toEqual(["Passports"]);
  }, 30_000);
});
