import type { AppEnv } from "../env.js";
import type { SafeConfigRow, SafeItemRow } from "../routes/safe.js";
import { sha256Hex } from "../security/password.js";
import {
  serializeCipher,
  serializeFolder,
  type CipherRow,
  type FolderRow,
  type UserRow,
} from "./serializer.js";

export interface BackupRunResult {
  key: string;
  sha256: string;
  userCount: number;
  folderCount: number;
  cipherCount: number;
  safeItemCount: number;
  bytes: number;
  prunedCount: number;
  createdAt: string;
}

/**
 * Exports a Zero-Knowledge encrypted vault snapshot from D1 to Cloudflare R2.
 * Deliberately excludes `master_password_hash`, `password_salt`, and `refresh_tokens`
 * while preserving everything required to decrypt or migrate the vault offline:
 * user KDF parameters, `symmetric_key` (Type 2), RSA keys, folders, ciphers, and Vault+ encrypted metadata.
 */
export async function runEncryptedR2Backup(
  env: AppEnv,
  trigger: "cron" | "manual",
): Promise<BackupRunResult> {
  const now = new Date();
  const createdAt = now.toISOString();
  const datePrefix = createdAt.slice(0, 10);
  const safeTs = createdAt.replace(/[:.]/g, "-");

  const [usersRes, foldersRes, ciphersRes, safeConfigsRes, safeItemsRes] = await Promise.all([
    env.DB.prepare(
      "SELECT id, email, name, master_password_hint, symmetric_key, public_key, private_key, kdf_type, kdf_iterations, kdf_memory, kdf_parallelism, revision_date, created_at, updated_at FROM users",
    ).all<Omit<UserRow, "master_password_hash" | "password_salt" | "server_kdf_iterations" | "security_stamp">>(),
    env.DB.prepare("SELECT * FROM folders ORDER BY created_at ASC").all<FolderRow>(),
    env.DB.prepare("SELECT * FROM ciphers ORDER BY created_at ASC").all<CipherRow>(),
    env.DB.prepare("SELECT * FROM personal_safe_configs ORDER BY created_at ASC").all<SafeConfigRow>(),
    env.DB.prepare("SELECT * FROM personal_safe_items WHERE status = 'ready' ORDER BY created_at ASC").all<SafeItemRow>(),
  ]);

  const users = usersRes.results ?? [];
  const folders = foldersRes.results ?? [];
  const ciphers = ciphersRes.results ?? [];
  const safeConfigs = safeConfigsRes.results ?? [];
  const safeItems = safeItemsRes.results ?? [];

  const snapshot = {
    schemaVersion: 2,
    format: "openvault-zero-knowledge-snapshot",
    createdAt,
    trigger,
    users: users.map((u) => ({
      id: u.id,
      email: u.email,
      name: u.name,
      masterPasswordHint: u.master_password_hint,
      key: u.symmetric_key,
      publicKey: u.public_key,
      privateKey: u.private_key,
      kdf: u.kdf_type,
      kdfIterations: u.kdf_iterations,
      kdfMemory: u.kdf_memory,
      kdfParallelism: u.kdf_parallelism,
      revisionDate: u.revision_date,
      createdAt: u.created_at,
    })),
    folders: folders.map((f) => ({
      userId: f.user_id,
      ...serializeFolder(f),
    })),
    ciphers: ciphers.map((c) => ({
      userId: c.user_id,
      ...serializeCipher(c),
    })),
    personalSafeConfigs: safeConfigs.map((sc) => ({
      userId: sc.user_id,
      version: sc.version,
      kdfType: sc.kdf_type,
      kdfIterations: sc.kdf_iterations,
      kdfMemory: sc.kdf_memory,
      kdfParallelism: sc.kdf_parallelism,
      passcodeSalt: sc.passcode_salt,
      passcodeEncryptedSafeKey: sc.passcode_encrypted_safe_key,
      recoverySalt: sc.recovery_salt,
      recoveryKdfIterations: sc.recovery_kdf_iterations,
      recoveryEncryptedSafeKey: sc.recovery_encrypted_safe_key,
      autoLockMinutes: sc.auto_lock_minutes,
      revisionDate: sc.revision_date,
      createdAt: sc.created_at,
    })),
    personalSafeItems: safeItems.map((si) => ({
      id: si.id,
      userId: si.user_id,
      category: si.category,
      encryptedMetadata: si.encrypted_metadata,
      r2BlobKey: si.r2_blob_key,
      r2ThumbKey: si.r2_thumb_key,
      blobSizeBytes: si.blob_size_bytes,
      thumbSizeBytes: si.thumb_size_bytes,
      blobSha256: si.blob_sha256,
      favorite: si.favorite === 1,
      deletedAt: si.deleted_at,
      revisionDate: si.revision_date,
      createdAt: si.created_at,
    })),
  };

  const payload = JSON.stringify(snapshot);
  const sha256 = await sha256Hex(payload);
  const key = `backups/${datePrefix}/openvault-${safeTs}.json`;

  await env.BACKUPS.put(key, payload, {
    httpMetadata: {
      contentType: "application/json; charset=utf-8",
    },
    customMetadata: {
      sha256,
      trigger,
      createdAt,
      cipherCount: String(ciphers.length),
      safeItemCount: String(safeItems.length),
    },
  });

  // Retention pruning: remove backups older than BACKUP_RETENTION_DAYS
  const retentionDays = Math.max(1, Number.parseInt(env.BACKUP_RETENTION_DAYS || "30", 10) || 30);
  const cutoffMs = now.getTime() - retentionDays * 24 * 60 * 60 * 1000;
  let prunedCount = 0;

  let cursor: string | undefined;
  do {
    const listed = await env.BACKUPS.list({ prefix: "backups/", cursor, limit: 100 });
    const expiredKeys = listed.objects
      .filter((obj) => obj.key !== key && obj.uploaded.getTime() < cutoffMs)
      .map((obj) => obj.key);

    if (expiredKeys.length > 0) {
      await env.BACKUPS.delete(expiredKeys);
      prunedCount += expiredKeys.length;
    }
    cursor = listed.truncated ? listed.cursor : undefined;
  } while (cursor);

  return {
    key,
    sha256,
    userCount: users.length,
    folderCount: folders.length,
    cipherCount: ciphers.length,
    safeItemCount: safeItems.length,
    bytes: new TextEncoder().encode(payload).byteLength,
    prunedCount,
    createdAt,
  };
}

export interface BackupListItem {
  key: string;
  size: number;
  uploaded: string;
  sha256?: string;
  trigger?: string;
  cipherCount?: string;
}

export async function listR2Backups(env: AppEnv, limit = 30): Promise<BackupListItem[]> {
  const listed = await env.BACKUPS.list({
    prefix: "backups/",
    limit: Math.min(Math.max(1, limit), 100),
    include: ["customMetadata"],
  });

  return listed.objects
    .map((obj) => ({
      key: obj.key,
      size: obj.size,
      uploaded: obj.uploaded.toISOString(),
      sha256: obj.customMetadata?.sha256,
      trigger: obj.customMetadata?.trigger,
      cipherCount: obj.customMetadata?.cipherCount,
    }))
    .sort((a, b) => b.uploaded.localeCompare(a.uploaded));
}
