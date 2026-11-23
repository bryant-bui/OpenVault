import path from "node:path";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [
    cloudflareTest(async () => {
      const migrationsPath = path.join(import.meta.dirname, "migrations");
      const migrations = await readD1Migrations(migrationsPath);

      return {
        config: true,
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migrations,
            JWT_SIGNING_SECRET: "test-jwt-signing-secret-must-be-at-least-32-chars-long-2026",
            PASSWORD_PEPPER: "test-password-pepper-must-be-at-least-32-chars-long-2026",
          },
        },
      };
    }),
  ],
  test: {
    setupFiles: ["./test/apply-migrations.ts"],
  },
});
