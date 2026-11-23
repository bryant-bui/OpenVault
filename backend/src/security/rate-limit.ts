import { sha256Hex } from "./password.js";

const WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const MAX_ATTEMPTS = 8;
const LOCKOUT_MS = 15 * 60 * 1000; // 15 minutes lockout after 8 failed logins

export interface RateLimitStatus {
  allowed: boolean;
  retryAfterSeconds: number;
}

export async function makeRateLimitKey(scope: string, ip: string, identifier: string): Promise<string> {
  const hash = await sha256Hex(`${scope}:${ip}:${identifier.trim().toLowerCase()}`);
  return `${scope}:${hash.slice(0, 32)}`;
}

export async function checkRateLimit(db: D1Database, key: string): Promise<RateLimitStatus> {
  const now = Date.now();
  const row = await db
    .prepare("SELECT attempts, window_start, locked_until FROM auth_rate_limits WHERE key = ?")
    .bind(key)
    .first<{ attempts: number; window_start: number; locked_until: number }>();

  if (!row) {
    return { allowed: true, retryAfterSeconds: 0 };
  }

  if (row.locked_until > now) {
    return {
      allowed: false,
      retryAfterSeconds: Math.ceil((row.locked_until - now) / 1000),
    };
  }

  return { allowed: true, retryAfterSeconds: 0 };
}

export async function recordFailedAttempt(db: D1Database, key: string): Promise<void> {
  const now = Date.now();
  const row = await db
    .prepare("SELECT attempts, window_start FROM auth_rate_limits WHERE key = ?")
    .bind(key)
    .first<{ attempts: number; window_start: number }>();

  if (!row || now - row.window_start > WINDOW_MS) {
    await db
      .prepare(
        "INSERT INTO auth_rate_limits (key, attempts, window_start, locked_until) VALUES (?, 1, ?, 0) ON CONFLICT(key) DO UPDATE SET attempts = 1, window_start = excluded.window_start, locked_until = 0",
      )
      .bind(key, now)
      .run();
    return;
  }

  const nextAttempts = row.attempts + 1;
  const lockedUntil = nextAttempts >= MAX_ATTEMPTS ? now + LOCKOUT_MS : 0;

  await db
    .prepare("UPDATE auth_rate_limits SET attempts = ?, locked_until = ? WHERE key = ?")
    .bind(nextAttempts, lockedUntil, key)
    .run();
}

export async function clearRateLimit(db: D1Database, key: string): Promise<void> {
  await db.prepare("DELETE FROM auth_rate_limits WHERE key = ?").bind(key).run();
}
