import {
  CipherType,
  KdfType,
  createPasskeyCredential,
  decryptCipher,
  decryptFolder,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptCipher,
  encryptFolder,
  generateProtectedRsaKeyPair,
  generateUserSymmetricKey,
  prepareBitwardenImport,
  protectSymmetricKey,
  stretchMasterKey,
  unprotectSymmetricKey,
  utf8ToBytes,
} from "@openvault/crypto-core";
import {
  applyD1Migrations,
  createExecutionContext,
  createScheduledController,
  reset,
  waitOnExecutionContext,
} from "cloudflare:test";
import { env, exports } from "cloudflare:workers";
import { beforeEach, describe, expect, it } from "vitest";
import worker from "../src/index.js";

async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  if (!exports.default) {
    throw new Error("Default worker export unavailable");
  }
  return exports.default.fetch(`https://vault.openvault.test${path}`, init);
}

async function registerAndLogin(email = "owner@openvault.test", masterPassword = "Master-Password!2026") {
  // Use PBKDF2 100,000 inside Worker tests (fast in workerd while still matching Bitwarden spec)
  const kdfConfig = { kdf: KdfType.PBKDF2_SHA256, kdfIterations: 100_000 };
  const masterKey = await deriveMasterKey(masterPassword, email, kdfConfig);
  const masterPasswordHash = await deriveMasterPasswordHash(masterKey, masterPassword, 1);
  const stretched = await stretchMasterKey(masterKey);
  const userKey = generateUserSymmetricKey();
  const protectedKey = await protectSymmetricKey(userKey, stretched);
  const rsa = await generateProtectedRsaKeyPair(userKey);

  const regRes = await apiFetch("/identity/accounts/register/finish", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email,
      name: "OpenVault Owner",
      masterPasswordHash,
      masterPasswordHint: "personal",
      userSymmetricKey: protectedKey,
      kdf: kdfConfig.kdf,
      kdfIterations: kdfConfig.kdfIterations,
      userAsymmetricKeys: rsa,
    }),
  });
  expect(regRes.status).toBe(200);

  const form = new URLSearchParams({
    grant_type: "password",
    username: email,
    password: masterPasswordHash,
    scope: "api offline_access",
    client_id: "browser",
    deviceType: "2",
    deviceIdentifier: "chrome-ext-1",
    deviceName: "Chrome",
  });
  const tokenRes = await apiFetch("/identity/connect/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  expect(tokenRes.status).toBe(200);
  const tokenJson = (await tokenRes.json()) as {
    access_token: string;
    refresh_token: string;
    Key: string;
    PrivateKey: string;
    Kdf: number;
    KdfIterations: number;
  };

  // Unprotect key from token response just like a real Bitwarden client
  const recoveredUserKey = await unprotectSymmetricKey(tokenJson.Key, stretched);

  return {
    email,
    masterPassword,
    masterPasswordHash,
    userKey: recoveredUserKey,
    accessToken: tokenJson.access_token,
    refreshToken: tokenJson.refresh_token,
    tokenJson,
  };
}

describe("OpenVault Backend — Bitwarden Zero-Knowledge API + D1 + R2", () => {
  beforeEach(async () => {
    await reset();
    await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
  });

  it("exposes health, server discovery /api/config, and anti-enumeration /prelogin", async () => {
    const health = await apiFetch("/health");
    expect(health.status).toBe(200);
    expect(health.headers.get("X-Content-Type-Options")).toBe("nosniff");
    expect(health.headers.get("Cache-Control")).toContain("no-store");

    const configRes = await apiFetch("/api/config");
    expect(configRes.status).toBe(200);
    const cfg = (await configRes.json()) as { server: { name: string }; featureStates: Record<string, boolean> };
    expect(cfg.server.name).toBe("OpenVault");
    expect(cfg.featureStates["fido2-vault-credentials"]).toBe(true);

    // Unknown email still returns plausible default KDF config (prevents account enumeration)
    const preloginRes = await apiFetch("/identity/accounts/prelogin", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: "nonexistent@example.com" }),
    });
    expect(preloginRes.status).toBe(200);
    const prelogin = (await preloginRes.json()) as { kdf: number; kdfIterations: number };
    expect(prelogin.kdf).toBe(1);
    expect(prelogin.kdfIterations).toBe(3);
  });

  it("completes full Zero-Knowledge vault lifecycle (Login + TOTP + Passkey + Folder + Sync + Trash)", async () => {
    const { accessToken, userKey } = await registerAndLogin();
    const authHeaders = {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    };

    // 1. Create encrypted folder
    const encFolder = await encryptFolder({ name: "Personal Accounts" }, userKey);
    const folderRes = await apiFetch("/api/folders", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({ name: encFolder.name }),
    });
    expect(folderRes.status).toBe(200);
    const createdFolder = (await folderRes.json()) as { id: string; name: string };

    // 2. Create encrypted Login cipher with TOTP + Passkey
    const passkey = await createPasskeyCredential({
      rpId: "github.com",
      rpName: "GitHub",
      userHandle: utf8ToBytes("uh-1"),
      userName: "owner@openvault.test",
    });
    const encCipher = await encryptCipher(
      {
        type: CipherType.Login,
        folderId: createdFolder.id,
        name: "GitHub Main",
        notes: "Primary dev account",
        favorite: true,
        login: {
          uris: [{ uri: "https://github.com/login" }],
          username: "bryant-bui",
          password: "UltraSecret#Password99",
          totp: "JBSWY3DPEHPK3PXP",
          fido2Credentials: [passkey.credential],
        },
      },
      userKey,
    );

    const createCipherRes = await apiFetch("/api/ciphers", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify(encCipher),
    });
    expect(createCipherRes.status).toBe(200);
    const createdCipher = (await createCipherRes.json()) as { id: string };

    // 3. Verify Zero-Knowledge guarantee: raw D1 row contains ZERO plaintext!
    const rawRow = await env.DB.prepare("SELECT data_json FROM ciphers WHERE id = ?")
      .bind(createdCipher.id)
      .first<{ data_json: string }>();
    expect(rawRow?.data_json).toBeDefined();
    expect(rawRow!.data_json).not.toContain("GitHub Main");
    expect(rawRow!.data_json).not.toContain("UltraSecret");
    expect(rawRow!.data_json).not.toContain("JBSWY3DPEHPK3PXP");

    // 4. Full Bitwarden /api/sync
    const syncRes = await apiFetch("/api/sync", { headers: { Authorization: `Bearer ${accessToken}` } });
    expect(syncRes.status).toBe(200);
    const syncData = (await syncRes.json()) as {
      object: string;
      profile: { premium: boolean };
      folders: { id: string; name: string }[];
      ciphers: { id: string; name: string; type: number; login: unknown }[];
    };
    expect(syncData.object).toBe("sync");
    expect(syncData.profile.premium).toBe(true);
    expect(syncData.folders).toHaveLength(1);
    expect(syncData.ciphers).toHaveLength(1);

    const decFolder = await decryptFolder(syncData.folders[0]!, userKey);
    expect(decFolder.name).toBe("Personal Accounts");

    const decCipher = await decryptCipher(syncData.ciphers[0] as never, userKey);
    expect(decCipher.name).toBe("GitHub Main");
    expect(decCipher.login?.password).toBe("UltraSecret#Password99");
    expect(decCipher.login?.totp).toBe("JBSWY3DPEHPK3PXP");
    expect(decCipher.login?.fido2Credentials?.[0]?.rpId).toBe("github.com");

    // 5. Soft delete -> Restore -> Hard delete
    const softDel = await apiFetch(`/api/ciphers/${createdCipher.id}/delete`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(softDel.status).toBe(204);

    const restore = await apiFetch(`/api/ciphers/${createdCipher.id}/restore`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(restore.status).toBe(200);
    expect(((await restore.json()) as { deletedDate: string | null }).deletedDate).toBeNull();

    const hardDel = await apiFetch(`/api/ciphers/${createdCipher.id}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(hardDel.status).toBe(204);
  });

  it("enforces MAX_USERS=1, rejects plaintext ciphers, detects refresh token replay, and rate-limits brute force", async () => {
    const { accessToken, refreshToken } = await registerAndLogin();

    // 1. Block second user registration when MAX_USERS=1
    const secondReg = await apiFetch("/identity/accounts/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        email: "attacker@example.com",
        masterPasswordHash: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        key: "2.AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAA==|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        kdf: 1,
        kdfIterations: 3,
      }),
    });
    expect(secondReg.status).toBe(403);

    // 2. Reject plaintext (non-Type 2) cipher strings
    const badCipher = await apiFetch("/api/ciphers", {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        type: 1,
        name: "PlaintextNotAllowed",
      }),
    });
    expect(badCipher.status).toBe(400);

    // 3. Refresh token rotation & replay-theft family revocation
    const rotate1 = await apiFetch("/identity/connect/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refreshToken,
      }).toString(),
    });
    expect(rotate1.status).toBe(200);
    const rotated = (await rotate1.json()) as { refresh_token: string };
    expect(rotated.refresh_token).not.toBe(refreshToken);

    // Replay old revoked refreshToken -> MUST reject AND revoke the newly rotated token too!
    const replay = await apiFetch("/identity/connect/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refreshToken,
      }).toString(),
    });
    expect(replay.status).toBe(400);

    const stolenChild = await apiFetch("/identity/connect/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: rotated.refresh_token,
      }).toString(),
    });
    expect(stolenChild.status).toBe(400);

    // 4. Rate-limit lockout after 8 failed password logins
    for (let i = 0; i < 8; i++) {
      const fail = await apiFetch("/identity/connect/token", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "CF-Connecting-IP": "203.0.113.10",
        },
        body: new URLSearchParams({
          grant_type: "password",
          username: "owner@openvault.test",
          password: "wrong-hash-value-padded-to-length-12345678",
        }).toString(),
      });
      expect(fail.status).toBe(400);
    }

    const locked = await apiFetch("/identity/connect/token", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        "CF-Connecting-IP": "203.0.113.10",
      },
      body: new URLSearchParams({
        grant_type: "password",
        username: "owner@openvault.test",
        password: "wrong-hash-value-padded-to-length-12345678",
      }).toString(),
    });
    expect(locked.status).toBe(429);
    expect(locked.headers.get("Retry-After")).toBeTruthy();
  });

  it("rotates security_stamp on Master Password change and invalidates old JWT immediately", async () => {
    const { accessToken, masterPasswordHash, userKey } = await registerAndLogin();

    const newMasterKey = await deriveMasterKey("BrandNew-Master!2027", "owner@openvault.test", {
      kdf: KdfType.PBKDF2_SHA256,
      kdfIterations: 100_000,
    });
    const newMasterHash = await deriveMasterPasswordHash(newMasterKey, "BrandNew-Master!2027", 1);
    const newStretched = await stretchMasterKey(newMasterKey);
    const newProtectedKey = await protectSymmetricKey(userKey, newStretched);

    const changeRes = await apiFetch("/api/accounts/password", {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        masterPasswordHash,
        newMasterPasswordHash: newMasterHash,
        key: newProtectedKey,
      }),
    });
    expect(changeRes.status).toBe(200);

    // Old JWT must now be rejected immediately because security_stamp rotated!
    const oldSync = await apiFetch("/api/sync", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(oldSync.status).toBe(401);
  });

  it("imports Bitwarden JSON batch and runs automated Cron + manual R2 encrypted snapshots", async () => {
    const { accessToken, userKey } = await registerAndLogin();

    const importPayload = await prepareBitwardenImport(
      {
        encrypted: false,
        folders: [{ id: "bw-f1", name: "Finance" }],
        items: [
          {
            folderId: "bw-f1",
            type: CipherType.Login,
            name: "Bank Vault",
            login: { username: "vip", password: "BankSecretPassword!" },
          },
        ],
      },
      userKey,
    );

    const impRes = await apiFetch("/api/ciphers/import", {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify(importPayload),
    });
    expect(impRes.status).toBe(200);
    const impBody = (await impRes.json()) as { importedFolders: number; importedCiphers: number };
    expect(impBody.importedFolders).toBe(1);
    expect(impBody.importedCiphers).toBe(1);

    // Run Scheduled Cron Trigger -> snapshots D1 into R2
    const controller = createScheduledController({
      scheduledTime: Date.now(),
      cron: "0 3 * * *",
    });
    const ctx = createExecutionContext();
    worker.scheduled(controller, env, ctx);
    await waitOnExecutionContext(ctx);

    // Verify backup listed via API and inspect R2 object contents
    const listRes = await apiFetch("/api/openvault/backups", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(listRes.status).toBe(200);
    const { backups } = (await listRes.json()) as {
      backups: { key: string; sha256?: string; cipherCount?: string }[];
    };
    expect(backups.length).toBe(1);
    expect(backups[0]!.sha256).toHaveLength(64);
    expect(backups[0]!.cipherCount).toBe("1");

    const r2Obj = await env.BACKUPS.get(backups[0]!.key);
    expect(r2Obj).not.toBeNull();
    const snapshotText = await r2Obj!.text();
    // Zero-knowledge + zero auth-secret leakage checks on R2 backup
    expect(snapshotText).toContain("openvault-zero-knowledge-snapshot");
    expect(snapshotText).not.toContain("BankSecretPassword");
    expect(snapshotText).not.toContain("master_password_hash");
    expect(snapshotText).not.toContain("password_salt");
  });
});
