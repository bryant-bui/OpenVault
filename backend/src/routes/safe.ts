import { Hono } from "hono";
import type { AppEnv } from "../env.js";
import { requireAuth, type AuthVariables } from "../middleware/auth.js";
import {
  safeItemCreateSchema,
  safeItemUpdateSchema,
  safePasscodeUpdateSchema,
  safeResetSchema,
  safeSettingsPatchSchema,
  safeSetupSchema,
  safeUnlockAttemptSchema,
} from "../schemas/safe.js";
import { sha256Hex, verifyServerPassword } from "../security/password.js";
import {
  checkRateLimit,
  clearRateLimit,
  makeRateLimitKey,
  recordFailedAttempt,
} from "../security/rate-limit.js";

export const SAFE_COOLDOWN_AFTER_FAILS = 5;
export const SAFE_COOLDOWN_MS = 15 * 60 * 1000;
export const SAFE_MAX_TOTAL_FAILS = 15;
export const OVSF_OVERHEAD_MARGIN = 64;
const OVSF_MIN_BYTES = 34;

export interface SafeConfigRow {
  user_id: string;
  version: number;
  kdf_type: number;
  kdf_iterations: number;
  kdf_memory: number | null;
  kdf_parallelism: number | null;
  passcode_salt: string;
  passcode_encrypted_safe_key: string;
  recovery_salt: string;
  recovery_kdf_iterations: number;
  recovery_encrypted_safe_key: string;
  auto_lock_minutes: number;
  failed_attempts: number;
  locked_until: number | null;
  recovery_required: number;
  revision_date: string;
  created_at: string;
  updated_at: string;
}

export interface SafeItemRow {
  id: string;
  user_id: string;
  status: string;
  category: string;
  encrypted_metadata: string;
  r2_blob_key: string;
  r2_thumb_key: string | null;
  blob_size_bytes: number;
  thumb_size_bytes: number;
  blob_sha256: string | null;
  favorite: number;
  deleted_at: string | null;
  revision_date: string;
  created_at: string;
  updated_at: string;
}

function parseIntOr(value: string | undefined, fallback: number): number {
  const n = Number.parseInt(value ?? "", 10);
  return Number.isFinite(n) ? n : fallback;
}

function getClientIp(headers: Headers): string {
  return (
    headers.get("CF-Connecting-IP") ??
    headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ??
    "unknown"
  );
}

function validateSafeKdfBounds(
  kdfType: number,
  kdfIterations: number,
  kdfMemory: number | null | undefined,
  kdfParallelism: number | null | undefined,
): string | null {
  if (kdfType === 0) {
    if (kdfIterations < 100_000 || kdfIterations > 2_000_000) {
      return "PBKDF2 iterations must be between 100,000 and 2,000,000";
    }
    return null;
  }
  if (kdfType === 1) {
    if (kdfIterations < 1 || kdfIterations > 10) {
      return "Argon2id iterations must be between 1 and 10";
    }
    const mem = kdfMemory ?? 64;
    const par = kdfParallelism ?? 4;
    if (mem < 15 || mem > 1024 || par < 1 || par > 16) {
      return "Invalid Argon2id memory or parallelism parameters";
    }
    return null;
  }
  return "Unsupported KDF type";
}

function serializeSafeConfig(
  row: SafeConfigRow,
  usage: { usedBytes: number; itemCount: number },
  limits: { maxFileBytes: number; maxThumbBytes: number },
  nowMs = Date.now(),
) {
  const isCoolingDown = row.locked_until !== null && row.locked_until > nowMs;
  const recoveryRequired = row.recovery_required === 1;
  const withholdPasscodeKey = isCoolingDown || recoveryRequired;

  return {
    initialized: true,
    version: row.version,
    kdfType: row.kdf_type,
    kdfIterations: row.kdf_iterations,
    kdfMemory: row.kdf_memory,
    kdfParallelism: row.kdf_parallelism,
    passcodeSalt: withholdPasscodeKey ? null : row.passcode_salt,
    passcodeEncryptedSafeKey: withholdPasscodeKey ? null : row.passcode_encrypted_safe_key,
    recoverySalt: row.recovery_salt,
    recoveryKdfIterations: row.recovery_kdf_iterations,
    recoveryEncryptedSafeKey: row.recovery_encrypted_safe_key,
    autoLockMinutes: row.auto_lock_minutes,
    failedAttempts: row.failed_attempts,
    lockedUntilMs: isCoolingDown ? row.locked_until : null,
    recoveryRequired,
    usedBytes: usage.usedBytes,
    itemCount: usage.itemCount,
    maxFileBytes: limits.maxFileBytes,
    maxThumbBytes: limits.maxThumbBytes,
    revisionDate: row.revision_date,
  };
}

export function serializeSafeItem(row: SafeItemRow) {
  return {
    id: row.id,
    status: row.status,
    category: row.category,
    encryptedMetadata: row.encrypted_metadata,
    hasThumbnail: Boolean(row.r2_thumb_key),
    blobSizeBytes: row.blob_size_bytes,
    thumbSizeBytes: row.thumb_size_bytes,
    blobSha256: row.blob_sha256,
    favorite: row.favorite === 1,
    deletedAt: row.deleted_at,
    revisionDate: row.revision_date,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function isValidOvsfHeader(bytes: Uint8Array): boolean {
  if (bytes.byteLength < OVSF_MIN_BYTES) return false;
  return (
    bytes[0] === 0x4f && // O
    bytes[1] === 0x56 && // V
    bytes[2] === 0x53 && // S
    bytes[3] === 0x46 && // F
    bytes[4] === 0x01 && // Version 1
    bytes[5] === 0x01 // Suite 1 (AES-256-GCM)
  );
}

type StreamReadResult =
  | { ok: true; bytes: Uint8Array; sha256: string }
  | { ok: false; status: 400 | 413; error: string; message: string };

async function readBoundedOvsfBody(
  request: Request,
  maxBytes: number,
): Promise<StreamReadResult> {
  const declaredLength = request.headers.get("Content-Length");
  if (declaredLength !== null) {
    const parsedLen = Number.parseInt(declaredLength, 10);
    if (Number.isFinite(parsedLen) && parsedLen > maxBytes) {
      return {
        ok: false,
        status: 413,
        error: "payload_too_large",
        message: `Payload exceeds maximum allowed size (${maxBytes} bytes)`,
      };
    }
  }

  const body = request.body;
  if (!body) {
    return {
      ok: false,
      status: 400,
      error: "missing_body",
      message: "Binary OVSF body is required",
    };
  }

  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!value || value.byteLength === 0) continue;

      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel("Exceeded maximum allowed size");
        return {
          ok: false,
          status: 413,
          error: "payload_too_large",
          message: `Payload exceeds maximum allowed size (${maxBytes} bytes)`,
        };
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const combined = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }

  if (!isValidOvsfHeader(combined)) {
    return {
      ok: false,
      status: 400,
      error: "invalid_ovsf_envelope",
      message: "Expected valid OVSF v1 binary envelope",
    };
  }

  const digest = await crypto.subtle.digest("SHA-256", combined);
  const sha256 = Array.from(new Uint8Array(digest), (b) =>
    b.toString(16).padStart(2, "0"),
  ).join("");

  return { ok: true, bytes: combined, sha256 };
}

export async function pruneExpiredSafeItems(
  env: AppEnv,
  now = new Date(),
): Promise<{ prunedPending: number; prunedTrash: number }> {
  const retentionDays = Math.max(
    1,
    parseIntOr(env.SAFE_TRASH_RETENTION_DAYS, 30),
  );
  const trashCutoff = new Date(
    now.getTime() - retentionDays * 24 * 60 * 60 * 1000,
  ).toISOString();
  const pendingCutoff = new Date(
    now.getTime() - 24 * 60 * 60 * 1000,
  ).toISOString();

  const [stalePendingRes, expiredTrashRes] = await Promise.all([
    env.DB.prepare(
      "SELECT id, r2_blob_key, r2_thumb_key FROM personal_safe_items WHERE status = 'pending' AND created_at < ?",
    )
      .bind(pendingCutoff)
      .all<{ id: string; r2_blob_key: string; r2_thumb_key: string | null }>(),
    env.DB.prepare(
      "SELECT id, r2_blob_key, r2_thumb_key FROM personal_safe_items WHERE deleted_at IS NOT NULL AND deleted_at < ?",
    )
      .bind(trashCutoff)
      .all<{ id: string; r2_blob_key: string; r2_thumb_key: string | null }>(),
  ]);

  const stalePending = stalePendingRes.results ?? [];
  const expiredTrash = expiredTrashRes.results ?? [];
  const allToDelete = [...stalePending, ...expiredTrash];

  if (allToDelete.length === 0) {
    return { prunedPending: 0, prunedTrash: 0 };
  }

  const r2Keys: string[] = [];
  for (const item of allToDelete) {
    r2Keys.push(item.r2_blob_key);
    if (item.r2_thumb_key) r2Keys.push(item.r2_thumb_key);
  }

  for (let i = 0; i < r2Keys.length; i += 100) {
    await env.SAFE_FILES.delete(r2Keys.slice(i, i + 100));
  }

  const statements = allToDelete.map((item) =>
    env.DB.prepare("DELETE FROM personal_safe_items WHERE id = ?").bind(item.id),
  );
  for (let i = 0; i < statements.length; i += 100) {
    await env.DB.batch(statements.slice(i, i + 100));
  }

  return {
    prunedPending: stalePending.length,
    prunedTrash: expiredTrash.length,
  };
}

export const safeRoutes = new Hono<{
  Bindings: AppEnv;
  Variables: AuthVariables;
}>();

safeRoutes.use("/api/openvault/safe/*", requireAuth);

safeRoutes.get("/api/openvault/safe/config", async (c) => {
  const user = c.get("user");
  const maxFileBytes = parseIntOr(c.env.SAFE_MAX_FILE_BYTES, 5 * 1024 * 1024);
  const maxThumbBytes = parseIntOr(c.env.SAFE_MAX_THUMB_BYTES, 128 * 1024);

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first<SafeConfigRow>();

  if (!row) {
    return c.json({
      initialized: false,
      maxFileBytes,
      maxThumbBytes,
    });
  }

  const usageRow = await c.env.DB.prepare(
    "SELECT COALESCE(SUM(blob_size_bytes + thumb_size_bytes), 0) AS used_bytes, COUNT(*) AS item_count FROM personal_safe_items WHERE user_id = ? AND status = 'ready'",
  )
    .bind(user.id)
    .first<{ used_bytes: number; item_count: number }>();

  return c.json(
    serializeSafeConfig(
      row,
      {
        usedBytes: Number(usageRow?.used_bytes ?? 0),
        itemCount: Number(usageRow?.item_count ?? 0),
      },
      { maxFileBytes, maxThumbBytes },
    ),
  );
});

safeRoutes.post("/api/openvault/safe/setup", async (c) => {
  const user = c.get("user");
  const maxFileBytes = parseIntOr(c.env.SAFE_MAX_FILE_BYTES, 5 * 1024 * 1024);
  const maxThumbBytes = parseIntOr(c.env.SAFE_MAX_THUMB_BYTES, 128 * 1024);

  const parsed = safeSetupSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const data = parsed.data;
  const kdfError = validateSafeKdfBounds(
    data.kdfType,
    data.kdfIterations,
    data.kdfMemory,
    data.kdfParallelism,
  );
  if (kdfError) {
    return c.json({ error: "validation_error", message: kdfError }, 400);
  }

  const existing = await c.env.DB.prepare(
    "SELECT user_id FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first();
  if (existing) {
    return c.json(
      { error: "already_initialized", message: "Vault+ is already configured" },
      409,
    );
  }

  const now = new Date().toISOString();
  const kdfMemory = data.kdfType === 1 ? (data.kdfMemory ?? 64) : null;
  const kdfParallelism = data.kdfType === 1 ? (data.kdfParallelism ?? 4) : null;

  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO personal_safe_configs (
        user_id, version, kdf_type, kdf_iterations, kdf_memory, kdf_parallelism,
        passcode_salt, passcode_encrypted_safe_key,
        recovery_salt, recovery_kdf_iterations, recovery_encrypted_safe_key,
        auto_lock_minutes, failed_attempts, locked_until, recovery_required,
        revision_date, created_at, updated_at
      ) VALUES (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, 0, ?, ?, ?)`,
    ).bind(
      user.id,
      data.kdfType,
      data.kdfIterations,
      kdfMemory,
      kdfParallelism,
      data.passcodeSalt,
      data.passcodeEncryptedSafeKey,
      data.recoverySalt,
      data.recoveryKdfIterations,
      data.recoveryEncryptedSafeKey,
      data.autoLockMinutes,
      now,
      now,
      now,
    ),
    c.env.DB.prepare(
      "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(
      crypto.randomUUID(),
      user.id,
      "safe_initialized",
      (await sha256Hex(getClientIp(c.req.raw.headers))).slice(0, 16),
      (c.req.header("User-Agent") ?? "unknown").slice(0, 200),
      now,
    ),
  ]);

  const created = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first<SafeConfigRow>();

  return c.json(
    serializeSafeConfig(
      created!,
      { usedBytes: 0, itemCount: 0 },
      { maxFileBytes, maxThumbBytes },
    ),
    201,
  );
});

safeRoutes.post("/api/openvault/safe/unlock-attempt", async (c) => {
  const user = c.get("user");
  const parsed = safeUnlockAttemptSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first<SafeConfigRow>();
  if (!row) {
    return c.json({ error: "not_initialized" }, 404);
  }

  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();

  if (row.recovery_required === 1) {
    return c.json(
      {
        error: "safe_recovery_required",
        message: "PIN attempts exhausted. Recovery Key or reset required.",
        failedAttempts: row.failed_attempts,
        lockedUntilMs: null,
        recoveryRequired: true,
      },
      423,
    );
  }

  if (row.locked_until !== null && row.locked_until > nowMs) {
    return c.json(
      {
        error: "safe_cooldown_active",
        message: "Too many failed PIN attempts. Cooldown active.",
        failedAttempts: row.failed_attempts,
        lockedUntilMs: row.locked_until,
        recoveryRequired: false,
        retryAfterSeconds: Math.ceil((row.locked_until - nowMs) / 1000),
      },
      429,
    );
  }

  if (parsed.data.success) {
    await c.env.DB.prepare(
      "UPDATE personal_safe_configs SET failed_attempts = 0, locked_until = NULL, updated_at = ? WHERE user_id = ?",
    )
      .bind(nowIso, user.id)
      .run();

    return c.json({
      ok: true,
      failedAttempts: 0,
      lockedUntilMs: null,
      recoveryRequired: false,
    });
  }

  const nextAttempts = row.failed_attempts + 1;
  const hitRecoveryLockout = nextAttempts >= SAFE_MAX_TOTAL_FAILS;
  const hitCooldown =
    !hitRecoveryLockout && nextAttempts % SAFE_COOLDOWN_AFTER_FAILS === 0;
  const nextLockedUntil = hitCooldown ? nowMs + SAFE_COOLDOWN_MS : null;
  const nextRecoveryRequired = hitRecoveryLockout ? 1 : 0;

  const statements: D1PreparedStatement[] = [
    c.env.DB.prepare(
      "UPDATE personal_safe_configs SET failed_attempts = ?, locked_until = ?, recovery_required = ?, updated_at = ? WHERE user_id = ?",
    ).bind(
      nextAttempts,
      nextLockedUntil,
      nextRecoveryRequired,
      nowIso,
      user.id,
    ),
  ];

  if (hitRecoveryLockout || hitCooldown) {
    statements.push(
      c.env.DB.prepare(
        "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind(
        crypto.randomUUID(),
        user.id,
        hitRecoveryLockout ? "safe_recovery_lockout" : "safe_pin_cooldown",
        (await sha256Hex(getClientIp(c.req.raw.headers))).slice(0, 16),
        (c.req.header("User-Agent") ?? "unknown").slice(0, 200),
        nowIso,
      ),
    );
  }

  await c.env.DB.batch(statements);

  return c.json({
    ok: false,
    failedAttempts: nextAttempts,
    lockedUntilMs: nextLockedUntil,
    recoveryRequired: hitRecoveryLockout,
  });
});

safeRoutes.put("/api/openvault/safe/passcode", async (c) => {
  const user = c.get("user");
  const maxFileBytes = parseIntOr(c.env.SAFE_MAX_FILE_BYTES, 5 * 1024 * 1024);
  const maxThumbBytes = parseIntOr(c.env.SAFE_MAX_THUMB_BYTES, 128 * 1024);

  const parsed = safePasscodeUpdateSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const data = parsed.data;
  const kdfError = validateSafeKdfBounds(
    data.kdfType,
    data.kdfIterations,
    data.kdfMemory,
    data.kdfParallelism,
  );
  if (kdfError) {
    return c.json({ error: "validation_error", message: kdfError }, 400);
  }

  const hasAnyRecoveryField =
    data.recoverySalt !== undefined ||
    data.recoveryKdfIterations !== undefined ||
    data.recoveryEncryptedSafeKey !== undefined;
  const hasAllRecoveryFields =
    data.recoverySalt !== undefined &&
    data.recoveryKdfIterations !== undefined &&
    data.recoveryEncryptedSafeKey !== undefined;
  if (hasAnyRecoveryField && !hasAllRecoveryFields) {
    return c.json(
      {
        error: "validation_error",
        message: "All recovery key wrapping fields must be provided together",
      },
      400,
    );
  }

  const existing = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first<SafeConfigRow>();
  if (!existing) {
    return c.json({ error: "not_initialized" }, 404);
  }

  const now = new Date().toISOString();
  const kdfMemory = data.kdfType === 1 ? (data.kdfMemory ?? 64) : null;
  const kdfParallelism = data.kdfType === 1 ? (data.kdfParallelism ?? 4) : null;

  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE personal_safe_configs
       SET kdf_type = ?, kdf_iterations = ?, kdf_memory = ?, kdf_parallelism = ?,
           passcode_salt = ?, passcode_encrypted_safe_key = ?,
           recovery_salt = COALESCE(?, recovery_salt),
           recovery_kdf_iterations = COALESCE(?, recovery_kdf_iterations),
           recovery_encrypted_safe_key = COALESCE(?, recovery_encrypted_safe_key),
           failed_attempts = 0, locked_until = NULL, recovery_required = 0,
           revision_date = ?, updated_at = ?
       WHERE user_id = ?`,
    ).bind(
      data.kdfType,
      data.kdfIterations,
      kdfMemory,
      kdfParallelism,
      data.passcodeSalt,
      data.passcodeEncryptedSafeKey,
      data.recoverySalt ?? null,
      data.recoveryKdfIterations ?? null,
      data.recoveryEncryptedSafeKey ?? null,
      now,
      now,
      user.id,
    ),
    c.env.DB.prepare(
      "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(
      crypto.randomUUID(),
      user.id,
      data.mode === "recovery" ? "safe_recovered" : "safe_passcode_changed",
      (await sha256Hex(getClientIp(c.req.raw.headers))).slice(0, 16),
      (c.req.header("User-Agent") ?? "unknown").slice(0, 200),
      now,
    ),
  ]);

  const [updated, usageRow] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM personal_safe_configs WHERE user_id = ?")
      .bind(user.id)
      .first<SafeConfigRow>(),
    c.env.DB.prepare(
      "SELECT COALESCE(SUM(blob_size_bytes + thumb_size_bytes), 0) AS used_bytes, COUNT(*) AS item_count FROM personal_safe_items WHERE user_id = ? AND status = 'ready'",
    )
      .bind(user.id)
      .first<{ used_bytes: number; item_count: number }>(),
  ]);

  return c.json(
    serializeSafeConfig(
      updated!,
      {
        usedBytes: Number(usageRow?.used_bytes ?? 0),
        itemCount: Number(usageRow?.item_count ?? 0),
      },
      { maxFileBytes, maxThumbBytes },
    ),
  );
});

safeRoutes.patch("/api/openvault/safe/settings", async (c) => {
  const user = c.get("user");
  const parsed = safeSettingsPatchSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const existing = await c.env.DB.prepare(
    "SELECT user_id FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first();
  if (!existing) {
    return c.json({ error: "not_initialized" }, 404);
  }

  const now = new Date().toISOString();
  await c.env.DB.prepare(
    "UPDATE personal_safe_configs SET auto_lock_minutes = ?, revision_date = ?, updated_at = ? WHERE user_id = ?",
  )
    .bind(parsed.data.autoLockMinutes, now, now, user.id)
    .run();

  return c.json({
    ok: true,
    autoLockMinutes: parsed.data.autoLockMinutes,
    revisionDate: now,
  });
});

safeRoutes.get("/api/openvault/safe/items", async (c) => {
  const user = c.get("user");
  const res = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE user_id = ? AND status = 'ready' ORDER BY favorite DESC, created_at DESC",
  )
    .bind(user.id)
    .all<SafeItemRow>();

  return c.json({
    items: (res.results ?? []).map(serializeSafeItem),
  });
});

safeRoutes.post("/api/openvault/safe/items", async (c) => {
  const user = c.get("user");
  const config = await c.env.DB.prepare(
    "SELECT user_id FROM personal_safe_configs WHERE user_id = ?",
  )
    .bind(user.id)
    .first();
  if (!config) {
    return c.json(
      { error: "not_initialized", message: "Vault+ must be set up first" },
      400,
    );
  }

  const parsed = safeItemCreateSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const id = crypto.randomUUID();
  const r2BlobKey = `safe/${user.id}/${id}.ovsf`;
  const now = new Date().toISOString();

  await c.env.DB.prepare(
    `INSERT INTO personal_safe_items (
      id, user_id, status, category, encrypted_metadata, r2_blob_key, r2_thumb_key,
      blob_size_bytes, thumb_size_bytes, blob_sha256, favorite, deleted_at, revision_date, created_at, updated_at
    ) VALUES (?, ?, 'pending', ?, ?, ?, NULL, 0, 0, NULL, ?, NULL, ?, ?, ?)`,
  )
    .bind(
      id,
      user.id,
      parsed.data.category,
      parsed.data.encryptedMetadata,
      r2BlobKey,
      parsed.data.favorite ? 1 : 0,
      now,
      now,
      now,
    )
    .run();

  return c.json({ id, revisionDate: now }, 201);
});

safeRoutes.put("/api/openvault/safe/items/:id/blob", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const maxFileBytes = parseIntOr(c.env.SAFE_MAX_FILE_BYTES, 5 * 1024 * 1024);

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!row || row.deleted_at !== null) {
    return c.json({ error: "not_found" }, 404);
  }

  const read = await readBoundedOvsfBody(
    c.req.raw,
    maxFileBytes + OVSF_OVERHEAD_MARGIN,
  );
  if (!read.ok) {
    return c.json({ error: read.error, message: read.message }, read.status);
  }

  await c.env.SAFE_FILES.put(row.r2_blob_key, read.bytes, {
    httpMetadata: {
      contentType: "application/octet-stream",
    },
    customMetadata: {
      itemId,
      userId: user.id,
      sha256: read.sha256,
      kind: "blob",
    },
  });

  const now = new Date().toISOString();
  await c.env.DB.prepare(
    `UPDATE personal_safe_items
     SET status = 'ready', blob_size_bytes = ?, blob_sha256 = ?, revision_date = ?, updated_at = ?
     WHERE id = ? AND user_id = ?`,
  )
    .bind(read.bytes.byteLength, read.sha256, now, now, itemId, user.id)
    .run();

  return c.json(
    serializeSafeItem({
      ...row,
      status: "ready",
      blob_size_bytes: read.bytes.byteLength,
      blob_sha256: read.sha256,
      revision_date: now,
      updated_at: now,
    }),
  );
});

safeRoutes.put("/api/openvault/safe/items/:id/thumbnail", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const maxThumbBytes = parseIntOr(c.env.SAFE_MAX_THUMB_BYTES, 128 * 1024);

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!row || row.deleted_at !== null) {
    return c.json({ error: "not_found" }, 404);
  }

  const read = await readBoundedOvsfBody(
    c.req.raw,
    maxThumbBytes + OVSF_OVERHEAD_MARGIN,
  );
  if (!read.ok) {
    return c.json({ error: read.error, message: read.message }, read.status);
  }

  const r2ThumbKey = `safe/${user.id}/${itemId}.thumb.ovsf`;
  await c.env.SAFE_FILES.put(r2ThumbKey, read.bytes, {
    httpMetadata: {
      contentType: "application/octet-stream",
    },
    customMetadata: {
      itemId,
      userId: user.id,
      sha256: read.sha256,
      kind: "thumb",
    },
  });

  const now = new Date().toISOString();
  await c.env.DB.prepare(
    "UPDATE personal_safe_items SET r2_thumb_key = ?, thumb_size_bytes = ?, revision_date = ?, updated_at = ? WHERE id = ? AND user_id = ?",
  )
    .bind(r2ThumbKey, read.bytes.byteLength, now, now, itemId, user.id)
    .run();

  return c.json({
    ok: true,
    thumbSizeBytes: read.bytes.byteLength,
    revisionDate: now,
  });
});

safeRoutes.get("/api/openvault/safe/items/:id/blob", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ? AND status = 'ready'",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!row) {
    return c.json({ error: "not_found" }, 404);
  }

  const object = await c.env.SAFE_FILES.get(row.r2_blob_key);
  if (!object) {
    return c.json({ error: "blob_not_found" }, 404);
  }

  return new Response(object.body, {
    status: 200,
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Length": String(object.size),
      "Cache-Control": "no-store, no-cache, must-revalidate, private",
      "X-Content-Type-Options": "nosniff",
    },
  });
});

safeRoutes.get("/api/openvault/safe/items/:id/thumbnail", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");

  const row = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ? AND status = 'ready'",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!row || !row.r2_thumb_key) {
    return c.json({ error: "not_found" }, 404);
  }

  const object = await c.env.SAFE_FILES.get(row.r2_thumb_key);
  if (!object) {
    return c.json({ error: "thumbnail_not_found" }, 404);
  }

  return new Response(object.body, {
    status: 200,
    headers: {
      "Content-Type": "application/octet-stream",
      "Content-Length": String(object.size),
      "Cache-Control": "no-store, no-cache, must-revalidate, private",
      "X-Content-Type-Options": "nosniff",
    },
  });
});

safeRoutes.put("/api/openvault/safe/items/:id", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const parsed = safeItemUpdateSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const existing = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!existing) {
    return c.json({ error: "not_found" }, 404);
  }

  const nextCategory = parsed.data.category ?? existing.category;
  const nextMeta = parsed.data.encryptedMetadata ?? existing.encrypted_metadata;
  const nextFav =
    parsed.data.favorite !== undefined
      ? parsed.data.favorite
        ? 1
        : 0
      : existing.favorite;
  const now = new Date().toISOString();

  await c.env.DB.prepare(
    "UPDATE personal_safe_items SET category = ?, encrypted_metadata = ?, favorite = ?, revision_date = ?, updated_at = ? WHERE id = ? AND user_id = ?",
  )
    .bind(nextCategory, nextMeta, nextFav, now, now, itemId, user.id)
    .run();

  return c.json(
    serializeSafeItem({
      ...existing,
      category: nextCategory,
      encrypted_metadata: nextMeta,
      favorite: nextFav,
      revision_date: now,
      updated_at: now,
    }),
  );
});

safeRoutes.put("/api/openvault/safe/items/:id/delete", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const existing = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!existing) {
    return c.json({ error: "not_found" }, 404);
  }

  const now = new Date().toISOString();
  await c.env.DB.prepare(
    "UPDATE personal_safe_items SET deleted_at = ?, revision_date = ?, updated_at = ? WHERE id = ? AND user_id = ?",
  )
    .bind(now, now, now, itemId, user.id)
    .run();

  return c.body(null, 204);
});

safeRoutes.put("/api/openvault/safe/items/:id/restore", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const existing = await c.env.DB.prepare(
    "SELECT * FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<SafeItemRow>();
  if (!existing) {
    return c.json({ error: "not_found" }, 404);
  }

  const now = new Date().toISOString();
  await c.env.DB.prepare(
    "UPDATE personal_safe_items SET deleted_at = NULL, revision_date = ?, updated_at = ? WHERE id = ? AND user_id = ?",
  )
    .bind(now, now, itemId, user.id)
    .run();

  return c.json(
    serializeSafeItem({
      ...existing,
      deleted_at: null,
      revision_date: now,
      updated_at: now,
    }),
  );
});

safeRoutes.delete("/api/openvault/safe/items/:id", async (c) => {
  const user = c.get("user");
  const itemId = c.req.param("id");
  const existing = await c.env.DB.prepare(
    "SELECT id, r2_blob_key, r2_thumb_key FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .first<{ id: string; r2_blob_key: string; r2_thumb_key: string | null }>();
  if (!existing) {
    return c.json({ error: "not_found" }, 404);
  }

  const keys = [existing.r2_blob_key];
  if (existing.r2_thumb_key) keys.push(existing.r2_thumb_key);
  await c.env.SAFE_FILES.delete(keys);

  await c.env.DB.prepare(
    "DELETE FROM personal_safe_items WHERE id = ? AND user_id = ?",
  )
    .bind(itemId, user.id)
    .run();

  return c.body(null, 204);
});

safeRoutes.delete("/api/openvault/safe/reset", async (c) => {
  const user = c.get("user");
  const parsed = safeResetSchema.safeParse(
    await c.req.json().catch(() => null),
  );
  if (!parsed.success) {
    return c.json(
      { error: "validation_error", details: parsed.error.flatten().fieldErrors },
      400,
    );
  }

  const ip = getClientIp(c.req.raw.headers);
  const rlKey = await makeRateLimitKey("safe_reset", ip, user.id);
  const rl = await checkRateLimit(c.env.DB, rlKey);
  if (!rl.allowed) {
    return c.json(
      {
        error: "rate_limited",
        message: `Too many reset attempts. Try again in ${rl.retryAfterSeconds}s`,
      },
      429,
    );
  }

  const valid = await verifyServerPassword(
    parsed.data.masterPasswordHash,
    user.master_password_hash,
    user.password_salt,
    c.env.PASSWORD_PEPPER,
    user.server_kdf_iterations,
  );
  if (!valid) {
    await recordFailedAttempt(c.env.DB, rlKey);
    return c.json(
      { error: "invalid_password", message: "Master Password is incorrect" },
      400,
    );
  }

  await clearRateLimit(c.env.DB, rlKey);

  // Delete all R2 objects under safe/<userId>/
  let cursor: string | undefined;
  do {
    const listed = await c.env.SAFE_FILES.list({
      prefix: `safe/${user.id}/`,
      cursor,
      limit: 100,
    });
    if (listed.objects.length > 0) {
      await c.env.SAFE_FILES.delete(listed.objects.map((o) => o.key));
    }
    cursor = listed.truncated ? listed.cursor : undefined;
  } while (cursor);

  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM personal_safe_items WHERE user_id = ?").bind(
      user.id,
    ),
    c.env.DB.prepare(
      "DELETE FROM personal_safe_configs WHERE user_id = ?",
    ).bind(user.id),
    c.env.DB.prepare(
      "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(
      crypto.randomUUID(),
      user.id,
      "safe_reset",
      (await sha256Hex(ip)).slice(0, 16),
      (c.req.header("User-Agent") ?? "unknown").slice(0, 200),
      now,
    ),
  ]);

  return c.json({ ok: true, resetAt: now });
});
