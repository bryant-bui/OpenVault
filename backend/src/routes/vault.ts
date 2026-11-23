import { Hono } from "hono";
import type { AppEnv } from "../env.js";
import { requireAuth, type AuthVariables } from "../middleware/auth.js";
import {
  changePasswordSchema,
  cipherInputSchema,
  folderInputSchema,
  importVaultSchema,
  type CipherInput,
} from "../schemas/bitwarden.js";
import {
  WORKER_MAX_PBKDF2_ITERATIONS,
  generateSaltB64,
  hashServerPassword,
  sha256Hex,
  verifyServerPassword,
} from "../security/password.js";
import { listR2Backups, runEncryptedR2Backup } from "../services/backup.js";
import {
  serializeCipher,
  serializeFolder,
  serializeProfile,
  serializeServerConfig,
  type CipherRow,
  type FolderRow,
} from "../services/serializer.js";

export const vaultRoutes = new Hono<{
  Bindings: AppEnv;
  Variables: AuthVariables;
}>();

function extractCipherDataJson(input: CipherInput): string {
  return JSON.stringify({
    name: input.name,
    notes: input.notes ?? null,
    login: input.login ?? null,
    secureNote: input.secureNote ?? null,
    card: input.card ?? null,
    identity: input.identity ?? null,
    fields: input.fields ?? null,
    passwordHistory: input.passwordHistory ?? null,
  });
}

// Public Bitwarden Server Discovery
vaultRoutes.get("/api/config", (c) => {
  const origin = new URL(c.req.url).origin;
  return c.json(serializeServerConfig(origin));
});

// All remaining /api/* endpoints require valid Bearer JWT + active security_stamp
vaultRoutes.use("/api/*", requireAuth);

// --- Account & Sync ---

vaultRoutes.get("/api/accounts/revision-date", (c) => {
  const user = c.get("user");
  const epochMs = Date.parse(user.revision_date);
  return c.json(Number.isFinite(epochMs) ? epochMs : Date.now());
});

vaultRoutes.get("/api/accounts/profile", (c) => {
  return c.json(serializeProfile(c.get("user")));
});

vaultRoutes.get("/api/sync", async (c) => {
  const user = c.get("user");

  const [foldersRes, ciphersRes] = await Promise.all([
    c.env.DB.prepare("SELECT * FROM folders WHERE user_id = ? ORDER BY created_at ASC")
      .bind(user.id)
      .all<FolderRow>(),
    c.env.DB.prepare("SELECT * FROM ciphers WHERE user_id = ? ORDER BY created_at ASC")
      .bind(user.id)
      .all<CipherRow>(),
  ]);

  return c.json({
    object: "sync",
    profile: serializeProfile(user),
    folders: (foldersRes.results ?? []).map(serializeFolder),
    collections: [],
    ciphers: (ciphersRes.results ?? []).map(serializeCipher),
    policies: [],
    domains: {
      object: "domains",
      equivalentDomains: null,
      globalEquivalentDomains: [],
    },
    sends: [],
  });
});

vaultRoutes.post("/api/accounts/password", async (c) => {
  const user = c.get("user");
  const raw = await c.req.json().catch(() => null);
  const parsed = changePasswordSchema.safeParse(raw);
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const valid = await verifyServerPassword(
    parsed.data.masterPasswordHash,
    user.master_password_hash,
    user.password_salt,
    c.env.PASSWORD_PEPPER,
    user.server_kdf_iterations,
  );
  if (!valid) {
    return c.json({ error: "invalid_password", message: "Current master password is incorrect" }, 400);
  }

  const nextSalt = generateSaltB64();
  const serverIterations = Math.min(
    Number.parseInt(c.env.SERVER_KDF_ITERATIONS || "100000", 10) || WORKER_MAX_PBKDF2_ITERATIONS,
    WORKER_MAX_PBKDF2_ITERATIONS,
  );
  const nextServerHash = await hashServerPassword(
    parsed.data.newMasterPasswordHash,
    nextSalt,
    c.env.PASSWORD_PEPPER,
    serverIterations,
  );
  const now = new Date().toISOString();
  const nextSecurityStamp = crypto.randomUUID();

  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE users
       SET master_password_hash = ?, password_salt = ?, server_kdf_iterations = ?,
           symmetric_key = ?, master_password_hint = COALESCE(?, master_password_hint),
           security_stamp = ?, revision_date = ?, updated_at = ?
       WHERE id = ?`,
    ).bind(
      nextServerHash,
      nextSalt,
      serverIterations,
      parsed.data.key,
      parsed.data.masterPasswordHint ?? null,
      nextSecurityStamp,
      now,
      now,
      user.id,
    ),
    c.env.DB.prepare(
      "UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
    ).bind(now, user.id),
    c.env.DB.prepare(
      "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
    ).bind(
      crypto.randomUUID(),
      user.id,
      "master_password_changed",
      null,
      (c.req.header("User-Agent") ?? "unknown").slice(0, 200),
      now,
    ),
  ]);

  return c.json({ ok: true, revisionDate: now });
});

vaultRoutes.post("/api/accounts/logout", async (c) => {
  const user = c.get("user");
  const body = (await c.req.json().catch(() => ({}))) as {
    refreshToken?: string;
    allDevices?: boolean;
  };
  const now = new Date().toISOString();

  if (body.allDevices) {
    await c.env.DB.batch([
      c.env.DB.prepare("UPDATE users SET security_stamp = ?, updated_at = ? WHERE id = ?").bind(
        crypto.randomUUID(),
        now,
        user.id,
      ),
      c.env.DB.prepare(
        "UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
      ).bind(now, user.id),
    ]);
    return c.json({ ok: true, revokedAll: true });
  }

  if (body.refreshToken) {
    const hash = await sha256Hex(body.refreshToken);
    await c.env.DB.prepare(
      "UPDATE refresh_tokens SET revoked_at = ? WHERE token_hash = ? AND user_id = ?",
    )
      .bind(now, hash, user.id)
      .run();
  }

  return c.json({ ok: true });
});

// --- Folders CRUD ---

vaultRoutes.get("/api/folders", async (c) => {
  const user = c.get("user");
  const res = await c.env.DB.prepare(
    "SELECT * FROM folders WHERE user_id = ? ORDER BY created_at ASC",
  )
    .bind(user.id)
    .all<FolderRow>();

  return c.json({
    object: "list",
    data: (res.results ?? []).map(serializeFolder),
    continuationToken: null,
  });
});

vaultRoutes.get("/api/folders/:id", async (c) => {
  const user = c.get("user");
  const row = await c.env.DB.prepare("SELECT * FROM folders WHERE id = ? AND user_id = ?")
    .bind(c.req.param("id"), user.id)
    .first<FolderRow>();

  if (!row) return c.json({ error: "not_found" }, 404);
  return c.json(serializeFolder(row));
});

vaultRoutes.post("/api/folders", async (c) => {
  const user = c.get("user");
  const parsed = folderInputSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();

  await c.env.DB.batch([
    c.env.DB.prepare(
      "INSERT INTO folders (id, user_id, name, revision_date, created_at) VALUES (?, ?, ?, ?, ?)",
    ).bind(id, user.id, parsed.data.name, now, now),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.json(
    serializeFolder({
      id,
      user_id: user.id,
      name: parsed.data.name,
      revision_date: now,
      created_at: now,
    }),
    200,
  );
});

vaultRoutes.put("/api/folders/:id", async (c) => {
  const user = c.get("user");
  const folderId = c.req.param("id");
  const parsed = folderInputSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const existing = await c.env.DB.prepare("SELECT * FROM folders WHERE id = ? AND user_id = ?")
    .bind(folderId, user.id)
    .first<FolderRow>();
  if (!existing) return c.json({ error: "not_found" }, 404);

  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare(
      "UPDATE folders SET name = ?, revision_date = ? WHERE id = ? AND user_id = ?",
    ).bind(parsed.data.name, now, folderId, user.id),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.json(
    serializeFolder({
      ...existing,
      name: parsed.data.name,
      revision_date: now,
    }),
  );
});

vaultRoutes.delete("/api/folders/:id", async (c) => {
  const user = c.get("user");
  const folderId = c.req.param("id");
  const now = new Date().toISOString();

  const existing = await c.env.DB.prepare("SELECT id FROM folders WHERE id = ? AND user_id = ?")
    .bind(folderId, user.id)
    .first();
  if (!existing) return c.json({ error: "not_found" }, 404);

  await c.env.DB.batch([
    c.env.DB.prepare("UPDATE ciphers SET folder_id = NULL, revision_date = ? WHERE folder_id = ? AND user_id = ?").bind(
      now,
      folderId,
      user.id,
    ),
    c.env.DB.prepare("DELETE FROM folders WHERE id = ? AND user_id = ?").bind(folderId, user.id),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.body(null, 204);
});

// --- Ciphers CRUD + Trash + Batch Import ---

vaultRoutes.get("/api/ciphers", async (c) => {
  const user = c.get("user");
  const res = await c.env.DB.prepare(
    "SELECT * FROM ciphers WHERE user_id = ? ORDER BY created_at ASC",
  )
    .bind(user.id)
    .all<CipherRow>();

  return c.json({
    object: "list",
    data: (res.results ?? []).map(serializeCipher),
    continuationToken: null,
  });
});

vaultRoutes.get("/api/ciphers/:id", async (c) => {
  const user = c.get("user");
  const row = await c.env.DB.prepare("SELECT * FROM ciphers WHERE id = ? AND user_id = ?")
    .bind(c.req.param("id"), user.id)
    .first<CipherRow>();
  if (!row) return c.json({ error: "not_found" }, 404);
  return c.json(serializeCipher(row));
});

vaultRoutes.post("/api/ciphers", async (c) => {
  const user = c.get("user");
  const parsed = cipherInputSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const input = parsed.data;
  if (input.folderId) {
    const folder = await c.env.DB.prepare("SELECT id FROM folders WHERE id = ? AND user_id = ?")
      .bind(input.folderId, user.id)
      .first();
    if (!folder) {
      return c.json({ error: "invalid_folder", message: "Folder not found" }, 400);
    }
  }

  const id = crypto.randomUUID();
  const now = new Date().toISOString();
  const dataJson = extractCipherDataJson(input);

  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO ciphers (id, user_id, folder_id, type, data_json, favorite, reprompt, deleted_at, revision_date, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)`,
    ).bind(
      id,
      user.id,
      input.folderId ?? null,
      input.type,
      dataJson,
      input.favorite ? 1 : 0,
      input.reprompt ?? 0,
      now,
      now,
    ),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.json(
    serializeCipher({
      id,
      user_id: user.id,
      folder_id: input.folderId ?? null,
      type: input.type,
      data_json: dataJson,
      favorite: input.favorite ? 1 : 0,
      reprompt: input.reprompt ?? 0,
      deleted_at: null,
      revision_date: now,
      created_at: now,
    }),
    200,
  );
});

vaultRoutes.put("/api/ciphers/:id", async (c) => {
  const user = c.get("user");
  const cipherId = c.req.param("id");
  const parsed = cipherInputSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const existing = await c.env.DB.prepare("SELECT * FROM ciphers WHERE id = ? AND user_id = ?")
    .bind(cipherId, user.id)
    .first<CipherRow>();
  if (!existing) return c.json({ error: "not_found" }, 404);

  const input = parsed.data;
  if (input.folderId) {
    const folder = await c.env.DB.prepare("SELECT id FROM folders WHERE id = ? AND user_id = ?")
      .bind(input.folderId, user.id)
      .first();
    if (!folder) {
      return c.json({ error: "invalid_folder", message: "Folder not found" }, 400);
    }
  }

  const now = new Date().toISOString();
  const dataJson = extractCipherDataJson(input);

  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE ciphers
       SET folder_id = ?, type = ?, data_json = ?, favorite = ?, reprompt = ?, revision_date = ?
       WHERE id = ? AND user_id = ?`,
    ).bind(
      input.folderId ?? null,
      input.type,
      dataJson,
      input.favorite ? 1 : 0,
      input.reprompt ?? 0,
      now,
      cipherId,
      user.id,
    ),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.json(
    serializeCipher({
      ...existing,
      folder_id: input.folderId ?? null,
      type: input.type,
      data_json: dataJson,
      favorite: input.favorite ? 1 : 0,
      reprompt: input.reprompt ?? 0,
      revision_date: now,
    }),
  );
});

// Bitwarden Soft-Delete (move to Trash)
vaultRoutes.put("/api/ciphers/:id/delete", async (c) => {
  const user = c.get("user");
  const cipherId = c.req.param("id");
  const existing = await c.env.DB.prepare("SELECT id FROM ciphers WHERE id = ? AND user_id = ?")
    .bind(cipherId, user.id)
    .first();
  if (!existing) return c.json({ error: "not_found" }, 404);

  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare(
      "UPDATE ciphers SET deleted_at = ?, revision_date = ? WHERE id = ? AND user_id = ?",
    ).bind(now, now, cipherId, user.id),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.body(null, 204);
});

// Bitwarden Restore from Trash
vaultRoutes.put("/api/ciphers/:id/restore", async (c) => {
  const user = c.get("user");
  const cipherId = c.req.param("id");
  const existing = await c.env.DB.prepare("SELECT * FROM ciphers WHERE id = ? AND user_id = ?")
    .bind(cipherId, user.id)
    .first<CipherRow>();
  if (!existing) return c.json({ error: "not_found" }, 404);

  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare(
      "UPDATE ciphers SET deleted_at = NULL, revision_date = ? WHERE id = ? AND user_id = ?",
    ).bind(now, cipherId, user.id),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.json(serializeCipher({ ...existing, deleted_at: null, revision_date: now }));
});

// Bitwarden Hard-Delete
vaultRoutes.delete("/api/ciphers/:id", async (c) => {
  const user = c.get("user");
  const cipherId = c.req.param("id");
  const existing = await c.env.DB.prepare("SELECT id FROM ciphers WHERE id = ? AND user_id = ?")
    .bind(cipherId, user.id)
    .first();
  if (!existing) return c.json({ error: "not_found" }, 404);

  const now = new Date().toISOString();
  await c.env.DB.batch([
    c.env.DB.prepare("DELETE FROM ciphers WHERE id = ? AND user_id = ?").bind(cipherId, user.id),
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  ]);

  return c.body(null, 204);
});

// Bitwarden Batch Import (Folders + Ciphers + Relationships)
vaultRoutes.post("/api/ciphers/import", async (c) => {
  const user = c.get("user");
  const parsed = importVaultSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) {
    return c.json({ error: "validation_error", details: parsed.error.flatten().fieldErrors }, 400);
  }

  const { folders, ciphers, folderRelationships } = parsed.data;
  const now = new Date().toISOString();

  const createdFolderIds: string[] = [];
  const statements: D1PreparedStatement[] = [];

  for (const folder of folders) {
    const folderId = crypto.randomUUID();
    createdFolderIds.push(folderId);
    statements.push(
      c.env.DB.prepare(
        "INSERT INTO folders (id, user_id, name, revision_date, created_at) VALUES (?, ?, ?, ?, ?)",
      ).bind(folderId, user.id, folder.name, now, now),
    );
  }

  const cipherFolderMap = new Map<number, string>();
  for (const rel of folderRelationships) {
    const mappedFolderId = createdFolderIds[rel.value];
    if (mappedFolderId) {
      cipherFolderMap.set(rel.key, mappedFolderId);
    }
  }

  for (let i = 0; i < ciphers.length; i++) {
    const cipher = ciphers[i]!;
    const cipherId = crypto.randomUUID();
    const folderId = cipherFolderMap.get(i) ?? null;
    statements.push(
      c.env.DB.prepare(
        `INSERT INTO ciphers (id, user_id, folder_id, type, data_json, favorite, reprompt, deleted_at, revision_date, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)`,
      ).bind(
        cipherId,
        user.id,
        folderId,
        cipher.type,
        extractCipherDataJson(cipher),
        cipher.favorite ? 1 : 0,
        cipher.reprompt ?? 0,
        now,
        now,
      ),
    );
  }

  statements.push(
    c.env.DB.prepare("UPDATE users SET revision_date = ?, updated_at = ? WHERE id = ?").bind(
      now,
      now,
      user.id,
    ),
  );

  // Batch in chunks of 100 statements to stay comfortably within D1 batch limits
  const BATCH_SIZE = 100;
  for (let i = 0; i < statements.length; i += BATCH_SIZE) {
    await c.env.DB.batch(statements.slice(i, i + BATCH_SIZE));
  }

  return c.json({
    ok: true,
    importedFolders: folders.length,
    importedCiphers: ciphers.length,
    revisionDate: now,
  });
});

// --- OpenVault R2 Encrypted Backups ---

vaultRoutes.post("/api/openvault/backup", async (c) => {
  const result = await runEncryptedR2Backup(c.env, "manual");
  return c.json(result, 200);
});

vaultRoutes.get("/api/openvault/backups", async (c) => {
  const items = await listR2Backups(c.env, 30);
  return c.json({ backups: items });
});
