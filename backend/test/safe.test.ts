import {
  KdfType,
  OVSF_OVERHEAD_BYTES,
  SAFE_MAX_FILE_BYTES,
  bytesToBase64,
  bytesToUtf8,
  decryptSafeFileBinary,
  decryptSafeMetadata,
  deriveMasterKey,
  deriveMasterPasswordHash,
  encryptSafeFileBinary,
  encryptSafeMetadata,
  generateFileKey,
  generateRecoveryKey,
  generateSafeKey,
  generateUserSymmetricKey,
  protectSafeKeyWithPin,
  protectSafeKeyWithRecoveryKey,
  protectSymmetricKey,
  sha256HexBytes,
  stretchMasterKey,
  unprotectSafeKeyWithPin,
  unprotectSafeKeyWithRecoveryKey,
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

async function registerAndLogin(email = "safe-owner@openvault.test", masterPassword = "Master-Password!2026") {
  const kdfConfig = { kdf: KdfType.PBKDF2_SHA256, kdfIterations: 100_000 };
  const masterKey = await deriveMasterKey(masterPassword, email, kdfConfig);
  const masterPasswordHash = await deriveMasterPasswordHash(masterKey, masterPassword, 1);
  const stretched = await stretchMasterKey(masterKey);
  const userKey = generateUserSymmetricKey();
  const protectedKey = await protectSymmetricKey(userKey, stretched);

  const regRes = await apiFetch("/identity/accounts/register/finish", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email,
      name: "Vault+ Owner",
      masterPasswordHash,
      userSymmetricKey: protectedKey,
      kdf: kdfConfig.kdf,
      kdfIterations: kdfConfig.kdfIterations,
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
    deviceIdentifier: "chrome-ext-safe-1",
    deviceName: "Chrome",
  });
  const tokenRes = await apiFetch("/identity/connect/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  expect(tokenRes.status).toBe(200);
  const tokenJson = (await tokenRes.json()) as { access_token: string };

  return {
    email,
    masterPasswordHash,
    accessToken: tokenJson.access_token,
  };
}

describe("OpenVault Backend — Vault+ (Personal Safe) API + R2 Isolation + Lockout", () => {
  beforeEach(async () => {
    await reset();
    await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
  });

  it("handles full Vault+ lifecycle: setup, 2-step OVSF upload/download, thumbnail, trash, backup & hard delete", async () => {
    const { accessToken } = await registerAndLogin();
    const authHeaders = {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    };

    // Uninitialized config
    const cfg0 = await apiFetch("/api/openvault/safe/config", { headers: authHeaders });
    expect(cfg0.status).toBe(200);
    const cfg0Json = (await cfg0.json()) as { initialized: boolean; maxFileBytes: number };
    expect(cfg0Json.initialized).toBe(false);
    expect(cfg0Json.maxFileBytes).toBe(SAFE_MAX_FILE_BYTES);

    // Setup Vault+ with 6-digit PIN + Recovery Key
    const safeKey = generateSafeKey();
    const recovery = generateRecoveryKey();
    const pinKdf = { kdf: KdfType.PBKDF2_SHA256, kdfIterations: 100_000 };
    const pinWrapped = await protectSafeKeyWithPin(safeKey, "135790", pinKdf);
    const recWrapped = await protectSafeKeyWithRecoveryKey(safeKey, recovery.formatted);

    const setupRes = await apiFetch("/api/openvault/safe/setup", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({
        kdfType: pinWrapped.kdfConfig.kdf,
        kdfIterations: pinWrapped.kdfConfig.kdfIterations,
        passcodeSalt: pinWrapped.saltB64,
        passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
        recoverySalt: recWrapped.saltB64,
        recoveryKdfIterations: recWrapped.iterations,
        recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
        autoLockMinutes: 3,
      }),
    });
    expect(setupRes.status).toBe(201);

    // Duplicate setup returns 409
    const dupRes = await apiFetch("/api/openvault/safe/setup", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({
        kdfType: pinWrapped.kdfConfig.kdf,
        kdfIterations: pinWrapped.kdfConfig.kdfIterations,
        passcodeSalt: pinWrapped.saltB64,
        passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
        recoverySalt: recWrapped.saltB64,
        recoveryKdfIterations: recWrapped.iterations,
        recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
      }),
    });
    expect(dupRes.status).toBe(409);

    // Create pending item + encrypt file + upload blob & thumbnail
    const fileKey = generateFileKey();
    const thumbKey = generateFileKey();
    const plainDoc = utf8ToBytes("%PDF-1.7 Secret CCCD scan bytes 123456789");
    const plainThumb = utf8ToBytes("fake-jpeg-thumb-bytes");
    const docSha = await sha256HexBytes(plainDoc);

    const encMeta = await encryptSafeMetadata(
      {
        v: 1,
        title: "CCCD Gắn Chip",
        fileName: "cccd.pdf",
        mimeType: "application/pdf",
        category: "id_card",
        notes: "Mặt trước và sau",
        fileKeyB64: bytesToBase64(fileKey),
        thumbKeyB64: bytesToBase64(thumbKey),
        originalSize: plainDoc.byteLength,
        originalSha256: docSha,
        expiresAt: "2035-10-12",
      },
      safeKey,
    );

    const createItemRes = await apiFetch("/api/openvault/safe/items", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({
        category: "id_card",
        encryptedMetadata: encMeta,
        favorite: true,
      }),
    });
    expect(createItemRes.status).toBe(201);
    const { id: itemId } = (await createItemRes.json()) as { id: string };

    // Pending items must NOT appear in GET /items yet
    const listPending = await apiFetch("/api/openvault/safe/items", { headers: authHeaders });
    expect(((await listPending.json()) as { items: unknown[] }).items.length).toBe(0);

    // Reject invalid non-OVSF upload
    const badMagicRes = await apiFetch(`/api/openvault/safe/items/${itemId}/blob`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/octet-stream",
      },
      body: utf8ToBytes("NOT_OVSF_HEADER_DATA_PAD_TO_34_BYTES_123456"),
    });
    expect(badMagicRes.status).toBe(400);

    // Reject > 5 MiB upload with 413
    const oversized = new Uint8Array(SAFE_MAX_FILE_BYTES + 100);
    oversized.set([0x4f, 0x56, 0x53, 0x46, 0x01, 0x01], 0);
    const oversizeRes = await apiFetch(`/api/openvault/safe/items/${itemId}/blob`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/octet-stream",
      },
      body: oversized,
    });
    expect(oversizeRes.status).toBe(413);

    // Valid OVSF blob + thumbnail upload
    const encBlob = await encryptSafeFileBinary(plainDoc, fileKey, itemId, "blob");
    const uploadRes = await apiFetch(`/api/openvault/safe/items/${itemId}/blob`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/octet-stream",
      },
      body: encBlob,
    });
    expect(uploadRes.status).toBe(200);
    const uploadedItem = (await uploadRes.json()) as {
      id: string;
      status: string;
      blobSizeBytes: number;
    };
    expect(uploadedItem.status).toBe("ready");
    expect(uploadedItem.blobSizeBytes).toBe(plainDoc.byteLength + OVSF_OVERHEAD_BYTES);

    const encThumb = await encryptSafeFileBinary(plainThumb, thumbKey, itemId, "thumb");
    const thumbUploadRes = await apiFetch(`/api/openvault/safe/items/${itemId}/thumbnail`, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/octet-stream",
      },
      body: encThumb,
    });
    expect(thumbUploadRes.status).toBe(200);

    // List items & download + decrypt both blob and thumbnail
    const listReady = await apiFetch("/api/openvault/safe/items", { headers: authHeaders });
    const listJson = (await listReady.json()) as {
      items: { id: string; encryptedMetadata: string; hasThumbnail: boolean; favorite: boolean }[];
    };
    expect(listJson.items.length).toBe(1);
    expect(listJson.items[0]!.hasThumbnail).toBe(true);
    expect(listJson.items[0]!.favorite).toBe(true);

    const decMeta = await decryptSafeMetadata(listJson.items[0]!.encryptedMetadata, safeKey);
    expect(decMeta.title).toBe("CCCD Gắn Chip");

    const dlBlob = await apiFetch(`/api/openvault/safe/items/${itemId}/blob`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(dlBlob.status).toBe(200);
    const dlBytes = new Uint8Array(await dlBlob.arrayBuffer());
    expect(bytesToUtf8(await decryptSafeFileBinary(dlBytes, fileKey, itemId, "blob"))).toBe(
      "%PDF-1.7 Secret CCCD scan bytes 123456789",
    );

    const dlThumb = await apiFetch(`/api/openvault/safe/items/${itemId}/thumbnail`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(dlThumb.status).toBe(200);
    const dlThumbBytes = new Uint8Array(await dlThumb.arrayBuffer());
    expect(bytesToUtf8(await decryptSafeFileBinary(dlThumbBytes, thumbKey, itemId, "thumb"))).toBe(
      "fake-jpeg-thumb-bytes",
    );

    // Confirm R2 SAFE_FILES bucket contains the objects and BACKUPS does not
    const safeList = await env.SAFE_FILES.list();
    expect(safeList.objects.length).toBe(2);

    // Soft-delete -> Restore -> Hard-delete removes R2 objects
    const delRes = await apiFetch(`/api/openvault/safe/items/${itemId}/delete`, {
      method: "PUT",
      headers: authHeaders,
    });
    expect(delRes.status).toBe(204);

    const restoreRes = await apiFetch(`/api/openvault/safe/items/${itemId}/restore`, {
      method: "PUT",
      headers: authHeaders,
    });
    expect(restoreRes.status).toBe(200);

    // Run backup snapshot & confirm schemaVersion 2 includes safe metadata (encrypted)
    const controller = createScheduledController({
      scheduledTime: Date.now(),
      cron: "0 3 * * *",
    });
    const ctx = createExecutionContext();
    worker.scheduled(controller, env, ctx);
    await waitOnExecutionContext(ctx);

    const backupsList = await env.BACKUPS.list({ prefix: "backups/" });
    expect(backupsList.objects.length).toBe(1);
    const backupText = await (await env.BACKUPS.get(backupsList.objects[0]!.key))!.text();
    expect(backupText).toContain('"schemaVersion":2');
    expect(backupText).toContain('"personalSafeConfigs"');
    expect(backupText).toContain('"personalSafeItems"');
    expect(backupText).not.toContain("CCCD Gắn Chip");

    // Hard delete wipes R2 objects
    const hardDel = await apiFetch(`/api/openvault/safe/items/${itemId}`, {
      method: "DELETE",
      headers: authHeaders,
    });
    expect(hardDel.status).toBe(204);
    expect((await env.SAFE_FILES.list()).objects.length).toBe(0);
  });

  it("enforces 5-fail 15-min cooldown, 15-fail permanent PIN lockout, Recovery Key reset, and Master Password wipe", async () => {
    const { accessToken, masterPasswordHash } = await registerAndLogin();
    const authHeaders = {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    };

    const safeKey = generateSafeKey();
    const recovery = generateRecoveryKey();
    const pinKdf = { kdf: KdfType.PBKDF2_SHA256, kdfIterations: 100_000 };
    const pinWrapped = await protectSafeKeyWithPin(safeKey, "246810", pinKdf);
    const recWrapped = await protectSafeKeyWithRecoveryKey(safeKey, recovery.formatted);

    await apiFetch("/api/openvault/safe/setup", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({
        kdfType: pinWrapped.kdfConfig.kdf,
        kdfIterations: pinWrapped.kdfConfig.kdfIterations,
        passcodeSalt: pinWrapped.saltB64,
        passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
        recoverySalt: recWrapped.saltB64,
        recoveryKdfIterations: recWrapped.iterations,
        recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
      }),
    });

    // 5 wrong PIN attempts -> triggers 15-minute cooldown
    for (let i = 1; i <= 5; i++) {
      const res = await apiFetch("/api/openvault/safe/unlock-attempt", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ success: false }),
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as {
        failedAttempts: number;
        lockedUntilMs: number | null;
        recoveryRequired: boolean;
      };
      expect(body.failedAttempts).toBe(i);
      if (i < 5) expect(body.lockedUntilMs).toBeNull();
      else expect(body.lockedUntilMs).toBeGreaterThan(Date.now() + 14 * 60_000);
      expect(body.recoveryRequired).toBe(false);
    }

    // While cooling down, GET /config MUST withhold passcodeEncryptedSafeKey while keeping recoveryEncryptedSafeKey
    const coolingCfg = await (
      await apiFetch("/api/openvault/safe/config", { headers: authHeaders })
    ).json() as {
      passcodeEncryptedSafeKey: string | null;
      passcodeSalt: string | null;
      recoveryEncryptedSafeKey: string | null;
      lockedUntilMs: number | null;
    };
    expect(coolingCfg.passcodeEncryptedSafeKey).toBeNull();
    expect(coolingCfg.passcodeSalt).toBeNull();
    expect(coolingCfg.recoveryEncryptedSafeKey).toBe(recWrapped.encryptedSafeKey);
    expect(coolingCfg.lockedUntilMs).not.toBeNull();

    // Attempts during cooldown are rejected 429
    const blockedRes = await apiFetch("/api/openvault/safe/unlock-attempt", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({ success: true }),
    });
    expect(blockedRes.status).toBe(429);

    // Simulate cooldown expiry at attempt 5, then fail 5 more times -> attempt 10 triggers 2nd cooldown
    await env.DB.prepare("UPDATE personal_safe_configs SET locked_until = ?").bind(Date.now() - 1000).run();
    for (let i = 6; i <= 10; i++) {
      const res = await apiFetch("/api/openvault/safe/unlock-attempt", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ success: false }),
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as { failedAttempts: number; lockedUntilMs: number | null };
      if (i === 10) expect(body.lockedUntilMs).not.toBeNull();
    }

    // Simulate 2nd cooldown expiry, then fail 5 more times -> attempt 15 triggers recoveryRequired
    await env.DB.prepare("UPDATE personal_safe_configs SET locked_until = ?").bind(Date.now() - 1000).run();
    for (let i = 11; i <= 15; i++) {
      const res = await apiFetch("/api/openvault/safe/unlock-attempt", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ success: false }),
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as {
        failedAttempts: number;
        lockedUntilMs: number | null;
        recoveryRequired: boolean;
      };
      if (i === 15) {
        expect(body.recoveryRequired).toBe(true);
        expect(body.lockedUntilMs).toBeNull();
      }
    }

    // Config now withholds PIN key and flags recoveryRequired: true
    const lockedOutCfg = await (
      await apiFetch("/api/openvault/safe/config", { headers: authHeaders })
    ).json() as {
      passcodeEncryptedSafeKey: string | null;
      recoveryEncryptedSafeKey: string;
      recoverySalt: string;
      recoveryKdfIterations: number;
      recoveryRequired: boolean;
    };
    expect(lockedOutCfg.recoveryRequired).toBe(true);
    expect(lockedOutCfg.passcodeEncryptedSafeKey).toBeNull();

    // PIN unlock attempt returns 423 Locked
    const locked423 = await apiFetch("/api/openvault/safe/unlock-attempt", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({ success: true }),
    });
    expect(locked423.status).toBe(423);

    // Recover using standalone Recovery Key and set a new 6-digit PIN
    const recoveredSafeKey = await unprotectSafeKeyWithRecoveryKey(
      lockedOutCfg.recoveryEncryptedSafeKey,
      recovery.formatted,
      lockedOutCfg.recoverySalt,
      lockedOutCfg.recoveryKdfIterations,
    );
    const newPinWrapped = await protectSafeKeyWithPin(recoveredSafeKey, "987654", pinKdf);

    const recoverRes = await apiFetch("/api/openvault/safe/passcode", {
      method: "PUT",
      headers: authHeaders,
      body: JSON.stringify({
        mode: "recovery",
        kdfType: newPinWrapped.kdfConfig.kdf,
        kdfIterations: newPinWrapped.kdfConfig.kdfIterations,
        passcodeSalt: newPinWrapped.saltB64,
        passcodeEncryptedSafeKey: newPinWrapped.encryptedSafeKey,
      }),
    });
    expect(recoverRes.status).toBe(200);
    const afterRecover = (await recoverRes.json()) as {
      failedAttempts: number;
      recoveryRequired: boolean;
      passcodeEncryptedSafeKey: string | null;
    };
    expect(afterRecover.failedAttempts).toBe(0);
    expect(afterRecover.recoveryRequired).toBe(false);
    expect(afterRecover.passcodeEncryptedSafeKey).toBe(newPinWrapped.encryptedSafeKey);

    // Verify new PIN decrypts original SafeKey
    const verifiedSafeKey = await unprotectSafeKeyWithPin(
      afterRecover.passcodeEncryptedSafeKey!,
      "987654",
      newPinWrapped.saltB64,
      pinKdf,
    );
    expect(bytesToBase64(verifiedSafeKey.raw)).toBe(bytesToBase64(safeKey.raw));

    // Create a file and test Nuclear Reset (reject wrong password, wipe on right password)
    const itemRes = await apiFetch("/api/openvault/safe/items", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({ category: "passport", encryptedMetadata: recWrapped.encryptedSafeKey }),
    });
    const { id: wipeId } = (await itemRes.json()) as { id: string };
    await apiFetch(`/api/openvault/safe/items/${wipeId}/blob`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/octet-stream" },
      body: await encryptSafeFileBinary(utf8ToBytes("passport-pdf"), generateFileKey(), wipeId, "blob"),
    });
    expect((await env.SAFE_FILES.list()).objects.length).toBe(1);

    const badReset = await apiFetch("/api/openvault/safe/reset", {
      method: "DELETE",
      headers: authHeaders,
      body: JSON.stringify({ masterPasswordHash: "wrong-master-password-hash-padding-12345" }),
    });
    expect(badReset.status).toBe(400);

    const goodReset = await apiFetch("/api/openvault/safe/reset", {
      method: "DELETE",
      headers: authHeaders,
      body: JSON.stringify({ masterPasswordHash }),
    });
    expect(goodReset.status).toBe(200);
    expect((await env.SAFE_FILES.list()).objects.length).toBe(0);

    const cfgAfterReset = await (
      await apiFetch("/api/openvault/safe/config", { headers: authHeaders })
    ).json() as { initialized: boolean };
    expect(cfgAfterReset.initialized).toBe(false);
  });

  it("prunes stale pending uploads (>24h) and expired Vault+ Trash (>30d) during scheduled maintenance", async () => {
    const { accessToken } = await registerAndLogin();
    const authHeaders = {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    };

    const safeKey = generateSafeKey();
    const rec = generateRecoveryKey();
    const pinWrapped = await protectSafeKeyWithPin(safeKey, "112233", {
      kdf: KdfType.PBKDF2_SHA256,
      kdfIterations: 100_000,
    });
    const recWrapped = await protectSafeKeyWithRecoveryKey(safeKey, rec.formatted);

    await apiFetch("/api/openvault/safe/setup", {
      method: "POST",
      headers: authHeaders,
      body: JSON.stringify({
        kdfType: pinWrapped.kdfConfig.kdf,
        kdfIterations: pinWrapped.kdfConfig.kdfIterations,
        passcodeSalt: pinWrapped.saltB64,
        passcodeEncryptedSafeKey: pinWrapped.encryptedSafeKey,
        recoverySalt: recWrapped.saltB64,
        recoveryKdfIterations: recWrapped.iterations,
        recoveryEncryptedSafeKey: recWrapped.encryptedSafeKey,
      }),
    });

    // Item 1: stale pending > 24h
    const p1 = (await (
      await apiFetch("/api/openvault/safe/items", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ category: "other", encryptedMetadata: recWrapped.encryptedSafeKey }),
      })
    ).json()) as { id: string };
    const oldPendingIso = new Date(Date.now() - 48 * 60 * 60 * 1000).toISOString();
    await env.DB.prepare("UPDATE personal_safe_items SET created_at = ? WHERE id = ?")
      .bind(oldPendingIso, p1.id)
      .run();

    // Item 2: expired trash > 30d
    const t2 = (await (
      await apiFetch("/api/openvault/safe/items", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ category: "medical", encryptedMetadata: recWrapped.encryptedSafeKey }),
      })
    ).json()) as { id: string };
    await apiFetch(`/api/openvault/safe/items/${t2.id}/blob`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/octet-stream" },
      body: await encryptSafeFileBinary(utf8ToBytes("medical-record"), generateFileKey(), t2.id, "blob"),
    });
    const oldTrashIso = new Date(Date.now() - 35 * 24 * 60 * 60 * 1000).toISOString();
    await env.DB.prepare("UPDATE personal_safe_items SET deleted_at = ? WHERE id = ?")
      .bind(oldTrashIso, t2.id)
      .run();

    // Item 3: active item (must survive!)
    const a3 = (await (
      await apiFetch("/api/openvault/safe/items", {
        method: "POST",
        headers: authHeaders,
        body: JSON.stringify({ category: "photo", encryptedMetadata: recWrapped.encryptedSafeKey }),
      })
    ).json()) as { id: string };
    await apiFetch(`/api/openvault/safe/items/${a3.id}/blob`, {
      method: "PUT",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/octet-stream" },
      body: await encryptSafeFileBinary(utf8ToBytes("active-photo"), generateFileKey(), a3.id, "blob"),
    });

    expect((await env.SAFE_FILES.list()).objects.length).toBe(2);

    const controller = createScheduledController({
      scheduledTime: Date.now(),
      cron: "0 3 * * *",
    });
    const ctx = createExecutionContext();
    worker.scheduled(controller, env, ctx);
    await waitOnExecutionContext(ctx);

    // Only Item 3's R2 blob and D1 row should remain
    const remainingR2 = await env.SAFE_FILES.list();
    expect(remainingR2.objects.length).toBe(1);
    expect(remainingR2.objects[0]!.key).toContain(a3.id);

    const itemsAfter = (await (
      await apiFetch("/api/openvault/safe/items", { headers: authHeaders })
    ).json()) as { items: { id: string }[] };
    expect(itemsAfter.items.map((i) => i.id)).toEqual([a3.id]);
  });
});
