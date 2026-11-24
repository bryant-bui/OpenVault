import { Hono } from "hono";
import { cors } from "hono/cors";
import type { AppEnv } from "./env.js";
import { identityRoutes } from "./routes/identity.js";
import { pruneExpiredSafeItems, safeRoutes } from "./routes/safe.js";
import { vaultRoutes } from "./routes/vault.js";
import { runEncryptedR2Backup } from "./services/backup.js";

const app = new Hono<{ Bindings: AppEnv }>();

// Strict security headers on every response (no caching of vault payloads)
app.use("*", async (c, next) => {
  await next();
  c.header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
  c.header("X-Content-Type-Options", "nosniff");
  c.header("X-Frame-Options", "DENY");
  c.header("Referrer-Policy", "no-referrer");
  c.header("Cache-Control", "no-store, no-cache, must-revalidate, private");
  c.header("Pragma", "no-cache");
});

// CORS permitting browser extensions, official Bitwarden web/desktop clients, and local dev
app.use(
  "*",
  cors({
    origin: (origin) => {
      if (!origin) return "";
      if (
        origin.startsWith("chrome-extension://") ||
        origin.startsWith("moz-extension://") ||
        origin.startsWith("http://localhost:") ||
        origin.startsWith("http://127.0.0.1:") ||
        origin.startsWith("https://")
      ) {
        return origin;
      }
      return "";
    },
    allowMethods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allowHeaders: [
      "Authorization",
      "Content-Type",
      "Bitwarden-Client-Name",
      "Bitwarden-Client-Version",
      "Device-Type",
      "Auth-Email",
    ],
    maxAge: 86400,
  }),
);

app.get("/health", (c) => {
  return c.json({ status: "ok", service: "openvault-api" });
});

app.route("/", identityRoutes);
app.route("/", vaultRoutes);
app.route("/", safeRoutes);

app.notFound((c) => {
  return c.json({ error: "not_found", message: "Endpoint not found" }, 404);
});

app.onError((err, c) => {
  console.error(
    JSON.stringify({
      level: "error",
      message: "unhandled_worker_error",
      path: new URL(c.req.url).pathname,
      error: err instanceof Error ? err.message : "unknown_error",
    }),
  );
  return c.json({ error: "internal_error", message: "Internal server error" }, 500);
});

async function runMaintenanceAndBackup(env: AppEnv): Promise<void> {
  const now = new Date();
  const nowIso = now.toISOString();
  const staleWindowCutoff = now.getTime() - 24 * 60 * 60 * 1000;

  // 1. Clean expired refresh tokens and stale rate-limit windows
  await env.DB.batch([
    env.DB.prepare("DELETE FROM refresh_tokens WHERE expires_at < ? OR (revoked_at IS NOT NULL AND revoked_at < ?)").bind(
      nowIso,
      new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000).toISOString(),
    ),
    env.DB.prepare("DELETE FROM auth_rate_limits WHERE window_start < ? AND locked_until < ?").bind(
      staleWindowCutoff,
      now.getTime(),
    ),
  ]);

  // 2. Prune stale pending uploads and expired Vault+ Trash items
  const safePrune = await pruneExpiredSafeItems(env, now);

  // 3. Snapshot encrypted D1 vault to Cloudflare R2
  const result = await runEncryptedR2Backup(env, "cron");
  console.log(
    JSON.stringify({
      level: "info",
      message: "scheduled_r2_backup_completed",
      key: result.key,
      cipherCount: result.cipherCount,
      bytes: result.bytes,
      prunedCount: result.prunedCount,
      safePrunedPending: safePrune.prunedPending,
      safePrunedTrash: safePrune.prunedTrash,
    }),
  );
}

export default {
  fetch(request: Request, env: AppEnv, ctx: ExecutionContext): Promise<Response> | Response {
    return app.fetch(request, env, ctx);
  },
  scheduled(controller: ScheduledController, env: AppEnv, ctx: ExecutionContext): void {
    console.log(
      JSON.stringify({
        level: "info",
        message: "scheduled_cron_triggered",
        cron: controller.cron,
        scheduledTime: controller.scheduledTime,
      }),
    );
    ctx.waitUntil(runMaintenanceAndBackup(env));
  },
} satisfies ExportedHandler<AppEnv>;
