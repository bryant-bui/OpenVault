-- OpenVault D1 Schema (Bitwarden-compatible Zero-Knowledge Vault)
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  name TEXT,
  master_password_hash TEXT NOT NULL,
  password_salt TEXT NOT NULL,
  server_kdf_iterations INTEGER NOT NULL DEFAULT 100000,
  master_password_hint TEXT,
  symmetric_key TEXT NOT NULL,
  public_key TEXT,
  private_key TEXT,
  security_stamp TEXT NOT NULL,
  kdf_type INTEGER NOT NULL DEFAULT 1,
  kdf_iterations INTEGER NOT NULL DEFAULT 3,
  kdf_memory INTEGER DEFAULT 64,
  kdf_parallelism INTEGER DEFAULT 4,
  revision_date TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS folders (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  revision_date TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_folders_user_id ON folders(user_id);

CREATE TABLE IF NOT EXISTS ciphers (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  folder_id TEXT REFERENCES folders(id) ON DELETE SET NULL,
  type INTEGER NOT NULL,
  data_json TEXT NOT NULL,
  favorite INTEGER NOT NULL DEFAULT 0,
  reprompt INTEGER NOT NULL DEFAULT 0,
  deleted_at TEXT,
  revision_date TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ciphers_user_active ON ciphers(user_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_ciphers_user_folder ON ciphers(user_id, folder_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL UNIQUE,
  family_id TEXT NOT NULL,
  device_id TEXT,
  device_name TEXT,
  device_type INTEGER,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family_id ON refresh_tokens(family_id);

CREATE TABLE IF NOT EXISTS auth_rate_limits (
  key TEXT PRIMARY KEY,
  attempts INTEGER NOT NULL DEFAULT 0,
  window_start INTEGER NOT NULL,
  locked_until INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS auth_audit_logs (
  id TEXT PRIMARY KEY,
  user_id TEXT,
  event_type TEXT NOT NULL,
  ip_hash TEXT,
  user_agent TEXT,
  created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON auth_audit_logs(created_at);
