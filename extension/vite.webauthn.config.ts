import path from "node:path";
import { defineConfig } from "vite";

export default defineConfig({
  publicDir: false,
  build: {
    outDir: "dist",
    emptyOutDir: false,
    target: "es2022",
    lib: {
      entry: path.resolve(import.meta.dirname, "src/content/webauthn-main.ts"),
      name: "OpenVaultWebAuthnMain",
      formats: ["iife"],
      fileName: () => "webauthn-main.js",
    },
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
});
