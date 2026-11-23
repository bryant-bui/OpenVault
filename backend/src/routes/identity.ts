import { Hono } from "hono";
import type { AppEnv } from "../env.js";
import { preloginSchema, registerSchema } from "../schemas/bitwarden.js";
import { signAccessToken } from "../security/jwt.js";
import {
  WORKER_MAX_PBKDF2_ITERATIONS,
  generateOpaqueToken,
  generateSaltB64,
  hashServerPassword,
  sha256Hex,
  verifyServerPassword,
} from "../security/password.js";
import {
  checkRateLimit,
  clearRateLimit,
  makeRateLimitKey,
  recordFailedAttempt,
} from "../security/rate-limit.js";
import type { UserRow } from "../services/serializer.js";

const DUMMY_SALT_B64 = "AAAAAAAAAAAAAAAAAAAAAA==";

export const identityRoutes = new Hono<{ Bindings: AppEnv }>();

function getClientIp(headers: Headers): string {
  return headers.get("CF-Connecting-IP") ?? headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ?? "unknown";
}

function parseIntOr(value: string | undefined, fallback: number): number {
  const n = Number.parseInt(value ?? "", 10);
  return Number.isFinite(n) ? n : fallback;
}

function buildTokenResponse(
  user: UserRow,
  accessToken: string,
  refreshToken: string,
  ttlSeconds: number,
) {
  return {
    access_token: accessToken,
    expires_in: ttlSeconds,
    token_type: "Bearer",
    refresh_token: refreshToken,
    Key: user.symmetric_key,
    PrivateKey: user.private_key,
    Kdf: user.kdf_type,
    KdfIterations: user.kdf_iterations,
    KdfMemory: user.kdf_memory,
    KdfParallelism: user.kdf_parallelism,
    ForcePasswordReset: false,
    ResetMasterPassword: false,
    scope: "api offline_access",
    unofficialServer: true,
  };
}

async function handlePrelogin(db: D1Database, rawBody: unknown) {
  const parsed = preloginSchema.safeParse(rawBody);
  if (!parsed.success) {
    return { status: 400 as const, body: { error: "invalid_request", message: "Valid email is required" } };
  }

  const email = parsed.data.email.toLowerCase();
  const user = await db
    .prepare("SELECT kdf_type, kdf_iterations, kdf_memory, kdf_parallelism FROM users WHERE email = ?")
    .bind(email)
    .first<{
      kdf_type: number;
      kdf_iterations: number;
      kdf_memory: number | null;
      kdf_parallelism: number | null;
    }>();

  if (!user) {
    // Return plausible default KDF config to prevent account enumeration
    return {
      status: 200 as const,
      body: {
        kdf: 1,
        kdfIterations: 3,
        kdfMemory: 64,
        kdfParallelism: 4,
      },
    };
  }

  return {
    status: 200 as const,
    body: {
      kdf: user.kdf_type,
      kdfIterations: user.kdf_iterations,
      kdfMemory: user.kdf_memory,
      kdfParallelism: user.kdf_parallelism,
    },
  };
}

identityRoutes.post("/identity/accounts/prelogin", async (c) => {
  const body = await c.req.json().catch(() => null);
  const res = await handlePrelogin(c.env.DB, body);
  return c.json(res.body, res.status);
});

identityRoutes.post("/api/accounts/prelogin", async (c) => {
  const body = await c.req.json().catch(() => null);
  const res = await handlePrelogin(c.env.DB, body);
  return c.json(res.body, res.status);
});

async function handleRegister(env: AppEnv, rawBody: unknown, ip: string, userAgent: string) {
  if (env.ALLOW_SIGNUP !== "true") {
    return {
      status: 403 as const,
      body: {
        error: "signup_disabled",
        message: "Registration is disabled on this OpenVault instance",
      },
    };
  }

  const parsed = registerSchema.safeParse(rawBody);
  if (!parsed.success) {
    return {
      status: 400 as const,
      body: {
        error: "validation_error",
        message: "Invalid registration payload",
        details: parsed.error.flatten().fieldErrors,
      },
    };
  }

  const data = parsed.data;
  const symmetricKey = data.userSymmetricKey ?? data.key;
  if (!symmetricKey) {
    return {
      status: 400 as const,
      body: { error: "validation_error", message: "Missing encrypted symmetric key" },
    };
  }

  // Enforce KDF parameter bounds matching Bitwarden security minimums
  if (data.kdf === 0 && (data.kdfIterations < 100_000 || data.kdfIterations > 2_000_000)) {
    return {
      status: 400 as const,
      body: { error: "validation_error", message: "PBKDF2 iterations must be between 100,000 and 2,000,000" },
    };
  }
  if (data.kdf === 1 && (data.kdfIterations < 1 || data.kdfIterations > 10)) {
    return {
      status: 400 as const,
      body: { error: "validation_error", message: "Argon2id iterations must be between 1 and 10" },
    };
  }

  const maxUsers = Math.max(1, parseIntOr(env.MAX_USERS, 1));
  const countRow = await env.DB.prepare("SELECT COUNT(*) as cnt FROM users").first<{ cnt: number }>();
  if ((countRow?.cnt ?? 0) >= maxUsers) {
    return {
      status: 403 as const,
      body: {
        error: "max_users_reached",
        message: "This personal OpenVault instance has reached its user limit",
      },
    };
  }

  const email = data.email.toLowerCase();
  const existing = await env.DB.prepare("SELECT id FROM users WHERE email = ?").bind(email).first();
  if (existing) {
    return {
      status: 409 as const,
      body: { error: "email_taken", message: "An account with this email already exists" },
    };
  }

  const serverIterations = Math.min(
    parseIntOr(env.SERVER_KDF_ITERATIONS, WORKER_MAX_PBKDF2_ITERATIONS),
    WORKER_MAX_PBKDF2_ITERATIONS,
  );
  const saltB64 = generateSaltB64();
  const serverHash = await hashServerPassword(
    data.masterPasswordHash,
    saltB64,
    env.PASSWORD_PEPPER,
    serverIterations,
  );

  const now = new Date().toISOString();
  const userId = crypto.randomUUID();
  const securityStamp = crypto.randomUUID();
  const keyPair = data.userAsymmetricKeys ?? data.keys ?? null;

  await env.DB.prepare(
    `INSERT INTO users (
      id, email, name, master_password_hash, password_salt, server_kdf_iterations,
      master_password_hint, symmetric_key, public_key, private_key, security_stamp,
      kdf_type, kdf_iterations, kdf_memory, kdf_parallelism, revision_date, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  )
    .bind(
      userId,
      email,
      data.name ?? null,
      serverHash,
      saltB64,
      serverIterations,
      data.masterPasswordHint ?? null,
      symmetricKey,
      keyPair?.publicKey ?? null,
      keyPair?.encryptedPrivateKey ?? null,
      securityStamp,
      data.kdf,
      data.kdfIterations,
      data.kdf === 1 ? (data.kdfMemory ?? 64) : null,
      data.kdf === 1 ? (data.kdfParallelism ?? 4) : null,
      now,
      now,
      now,
    )
    .run();

  const ipHash = await sha256Hex(ip);
  await env.DB.prepare(
    "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
  )
    .bind(crypto.randomUUID(), userId, "account_registered", ipHash.slice(0, 32), userAgent.slice(0, 200), now)
    .run();

  return {
    status: 200 as const,
    body: {
      object: "register",
      id: userId,
      email,
      captchaBypassToken: null,
    },
  };
}

for (const path of [
  "/identity/accounts/register",
  "/identity/accounts/register/finish",
  "/api/accounts/register",
]) {
  identityRoutes.post(path, async (c) => {
    const body = await c.req.json().catch(() => null);
    const res = await handleRegister(
      c.env,
      body,
      getClientIp(c.req.raw.headers),
      c.req.header("User-Agent") ?? "unknown",
    );
    return c.json(res.body, res.status);
  });
}

async function parseTokenForm(req: Request): Promise<Record<string, string>> {
  const contentType = req.headers.get("Content-Type") ?? "";
  if (contentType.includes("application/json")) {
    const json = (await req.json().catch(() => ({}))) as Record<string, unknown>;
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(json)) {
      if (typeof v === "string" || typeof v === "number") out[k] = String(v);
    }
    return out;
  }
  if (contentType.includes("application/x-www-form-urlencoded")) {
    const formData = await req.formData();
    const out: Record<string, string> = {};
    formData.forEach((value, key) => {
      if (typeof value === "string") out[key] = value;
    });
    return out;
  }
  const text = await req.text();
  const params = new URLSearchParams(text);
  const out: Record<string, string> = {};
  for (const [k, v] of params.entries()) {
    out[k] = v;
  }
  return out;
}

identityRoutes.post("/identity/connect/token", async (c) => {
  const form = await parseTokenForm(c.req.raw);
  const grantType = form["grant_type"];
  const ip = getClientIp(c.req.raw.headers);
  const ipHash = (await sha256Hex(ip)).slice(0, 32);
  const userAgent = (c.req.header("User-Agent") ?? "unknown").slice(0, 200);
  const ttlSeconds = Math.max(300, parseIntOr(c.env.ACCESS_TOKEN_TTL_SECONDS, 3600));
  const refreshDays = Math.max(1, parseIntOr(c.env.REFRESH_TOKEN_TTL_DAYS, 30));

  if (grantType === "password") {
    const username = (form["username"] ?? "").trim().toLowerCase();
    const passwordHash = form["password"] ?? "";
    if (!username || !passwordHash) {
      return c.json(
        {
          error: "invalid_request",
          error_description: "Missing username or password",
        },
        400,
      );
    }

    const rateKey = await makeRateLimitKey("login", ip, username);
    const limit = await checkRateLimit(c.env.DB, rateKey);
    if (!limit.allowed) {
      c.header("Retry-After", String(limit.retryAfterSeconds));
      return c.json(
        {
          error: "too_many_requests",
          error_description: "Too many failed login attempts. Try again later.",
        },
        429,
      );
    }

    const user = await c.env.DB.prepare("SELECT * FROM users WHERE email = ?")
      .bind(username)
      .first<UserRow>();

    if (!user) {
      // Constant-work dummy hash to mitigate user-existence timing side channel
      const serverIter = Math.min(
        parseIntOr(c.env.SERVER_KDF_ITERATIONS, WORKER_MAX_PBKDF2_ITERATIONS),
        WORKER_MAX_PBKDF2_ITERATIONS,
      );
      await hashServerPassword(passwordHash, DUMMY_SALT_B64, c.env.PASSWORD_PEPPER, serverIter);
      await recordFailedAttempt(c.env.DB, rateKey);
      return c.json(
        {
          error: "invalid_grant",
          error_description: "invalid_username_or_password",
          ErrorModel: {
            Message: "Username or password is incorrect. Try again.",
            Object: "error",
          },
        },
        400,
      );
    }

    const valid = await verifyServerPassword(
      passwordHash,
      user.master_password_hash,
      user.password_salt,
      c.env.PASSWORD_PEPPER,
      user.server_kdf_iterations,
    );

    const now = new Date();
    const nowIso = now.toISOString();

    if (!valid) {
      await recordFailedAttempt(c.env.DB, rateKey);
      await c.env.DB.prepare(
        "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      )
        .bind(crypto.randomUUID(), user.id, "login_failed", ipHash, userAgent, nowIso)
        .run();

      return c.json(
        {
          error: "invalid_grant",
          error_description: "invalid_username_or_password",
          ErrorModel: {
            Message: "Username or password is incorrect. Try again.",
            Object: "error",
          },
        },
        400,
      );
    }

    await clearRateLimit(c.env.DB, rateKey);

    const accessToken = await signAccessToken(
      {
        sub: user.id,
        email: user.email,
        name: user.name ?? user.email,
        premium: true,
        sstamp: user.security_stamp,
        amr: ["Application"],
      },
      c.env.JWT_SIGNING_SECRET,
      ttlSeconds,
    );

    const refreshToken = generateOpaqueToken(32);
    const refreshHash = await sha256Hex(refreshToken);
    const familyId = crypto.randomUUID();
    const expiresAt = new Date(now.getTime() + refreshDays * 24 * 60 * 60 * 1000).toISOString();
    const deviceType = form["deviceType"] ? Number.parseInt(form["deviceType"], 10) : null;

    await c.env.DB.batch([
      c.env.DB.prepare(
        `INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, device_id, device_name, device_type, expires_at, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        crypto.randomUUID(),
        user.id,
        refreshHash,
        familyId,
        form["deviceIdentifier"] ?? null,
        form["deviceName"] ?? null,
        Number.isFinite(deviceType) ? deviceType : null,
        expiresAt,
        nowIso,
      ),
      c.env.DB.prepare(
        "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
      ).bind(crypto.randomUUID(), user.id, "login_success", ipHash, userAgent, nowIso),
    ]);

    return c.json(buildTokenResponse(user, accessToken, refreshToken, ttlSeconds), 200);
  }

  if (grantType === "refresh_token") {
    const providedRefresh = form["refresh_token"] ?? "";
    if (!providedRefresh) {
      return c.json({ error: "invalid_request", error_description: "Missing refresh_token" }, 400);
    }

    const providedHash = await sha256Hex(providedRefresh);
    const tokenRow = await c.env.DB.prepare(
      "SELECT id, user_id, family_id, device_id, device_name, device_type, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?",
    )
      .bind(providedHash)
      .first<{
        id: string;
        user_id: string;
        family_id: string;
        device_id: string | null;
        device_name: string | null;
        device_type: number | null;
        expires_at: string;
        revoked_at: string | null;
      }>();

    if (!tokenRow) {
      return c.json({ error: "invalid_grant", error_description: "Invalid refresh token" }, 400);
    }

    const now = new Date();
    const nowIso = now.toISOString();

    // Refresh token reuse / theft detection: revoke the ENTIRE token family
    if (tokenRow.revoked_at !== null) {
      await c.env.DB.batch([
        c.env.DB.prepare(
          "UPDATE refresh_tokens SET revoked_at = ? WHERE family_id = ? AND revoked_at IS NULL",
        ).bind(nowIso, tokenRow.family_id),
        c.env.DB.prepare(
          "INSERT INTO auth_audit_logs (id, user_id, event_type, ip_hash, user_agent, created_at) VALUES (?, ?, ?, ?, ?, ?)",
        ).bind(
          crypto.randomUUID(),
          tokenRow.user_id,
          "refresh_token_replay_detected",
          ipHash,
          userAgent,
          nowIso,
        ),
      ]);
      return c.json({ error: "invalid_grant", error_description: "Refresh token revoked" }, 400);
    }

    if (Date.parse(tokenRow.expires_at) <= now.getTime()) {
      return c.json({ error: "invalid_grant", error_description: "Refresh token expired" }, 400);
    }

    const user = await c.env.DB.prepare("SELECT * FROM users WHERE id = ?")
      .bind(tokenRow.user_id)
      .first<UserRow>();
    if (!user) {
      return c.json({ error: "invalid_grant", error_description: "Account not found" }, 400);
    }

    const nextRefresh = generateOpaqueToken(32);
    const nextRefreshHash = await sha256Hex(nextRefresh);
    const nextExpiresAt = new Date(now.getTime() + refreshDays * 24 * 60 * 60 * 1000).toISOString();

    await c.env.DB.batch([
      c.env.DB.prepare("UPDATE refresh_tokens SET revoked_at = ? WHERE id = ?").bind(
        nowIso,
        tokenRow.id,
      ),
      c.env.DB.prepare(
        `INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, device_id, device_name, device_type, expires_at, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        crypto.randomUUID(),
        user.id,
        nextRefreshHash,
        tokenRow.family_id,
        tokenRow.device_id,
        tokenRow.device_name,
        tokenRow.device_type,
        nextExpiresAt,
        nowIso,
      ),
    ]);

    const accessToken = await signAccessToken(
      {
        sub: user.id,
        email: user.email,
        name: user.name ?? user.email,
        premium: true,
        sstamp: user.security_stamp,
        amr: ["Application"],
      },
      c.env.JWT_SIGNING_SECRET,
      ttlSeconds,
    );

    return c.json(buildTokenResponse(user, accessToken, nextRefresh, ttlSeconds), 200);
  }

  return c.json({ error: "unsupported_grant_type" }, 400);
});
