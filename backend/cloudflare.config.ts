import { bindings, defineConfig, triggers } from "cf/config";

export default defineConfig({
  worker: {
    name: "openvault-api",
    entrypoint: "./src/index.ts",
    compatibilityDate: "2026-11-23",
    compatibilityFlags: ["nodejs_compat"],
    observability: {
      enabled: true,
      redactQueryString: true,
      logs: {
        enabled: true,
        headSamplingRate: 1,
      },
      traces: {
        enabled: true,
        headSamplingRate: 0.1,
      },
    },
    triggers: [
      // Daily automated encrypted D1 -> R2 snapshot at 03:00 UTC
      triggers.scheduled({ schedule: "0 3 * * *" }),
    ],
    env: {
      DB: bindings.d1({
        name: "openvault-db",
      }),
      BACKUPS: bindings.r2({
        name: "openvault-backups",
      }),
      SAFE_FILES: bindings.r2({
        name: "openvault-safe",
      }),
      // Set ALLOW_SIGNUP to "false" after registering your personal account
      ALLOW_SIGNUP: bindings.text("true"),
      // Hard cap on total registered users for personal self-hosting
      MAX_USERS: bindings.text("1"),
      ACCESS_TOKEN_TTL_SECONDS: bindings.text("3600"),
      REFRESH_TOKEN_TTL_DAYS: bindings.text("30"),
      BACKUP_RETENTION_DAYS: bindings.text("30"),
      SAFE_MAX_FILE_BYTES: bindings.text("5242880"),
      SAFE_MAX_THUMB_BYTES: bindings.text("131072"),
      SAFE_TRASH_RETENTION_DAYS: bindings.text("30"),
      SERVER_KDF_ITERATIONS: bindings.text("100000"),
      // Provision via Cloudflare secrets (never commit plaintext secrets)
      JWT_SIGNING_SECRET: bindings.secret(),
      PASSWORD_PEPPER: bindings.secret(),
    },
  },
});
