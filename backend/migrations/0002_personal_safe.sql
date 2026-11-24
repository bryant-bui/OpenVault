-- OpenVault Vault+ (Personal Safe) D1 Schema
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS personal_safe_configs (
  user_id TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  version INTEGER NOT NULL DEFAULT 1,
  kdf_type INTEGER NOT NULL DEFAULT 1,
  kdf_iterations INTEGER NOT NULL DEFAULT 3,
  kdf_memory INTEGER DEFAULT 64,
  kdf_parallelism INTEGER DEFAULT 4,
  passcode_salt TEXT NOT NULL,
  passcode_encrypted_safe_key TEXT NOT NULL,
  recovery_salt TEXT NOT NULL,
  recovery_kdf_iterations INTEGER NOT NULL DEFAULT 100000,
  recovery_encrypted_safe_key TEXT NOT NULL,
  auto_lock_minutes INTEGER NOT NULL DEFAULT 3,
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until INTEGER,
  recovery_required INTEGER NOT NULL DEFAULT 0,
  revision_date TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS personal_safe_items (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending',
  category TEXT NOT NULL DEFAULT 'other',
  encrypted_metadata TEXT NOT NULL,
  r2_blob_key TEXT NOT NULL UNIQUE,
  r2_thumb_key TEXT,
  blob_size_bytes INTEGER NOT NULL DEFAULT 0,
  thumb_size_bytes INTEGER NOT NULL DEFAULT 0,
  blob_sha256 TEXT,
  favorite INTEGER NOT NULL DEFAULT 0,
  deleted_at TEXT,
  revision_date TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_safe_items_user_status
  ON personal_safe_items(user_id, status, deleted_at);
