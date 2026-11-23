import path from "node:path";
import { defineConfig } from "vite";

export default defineConfig({
  publicDir: false,
  build: {
    outDir: "dist",
    emptyOutDir: false,
    target: "es2022",
    lib: {
      entry: path.resolve(import.meta.dirname, "src/content/autofill.ts"),
      name: "OpenVaultAutofill",
      formats: ["iife"],
      fileName: () => "content-autofill.js",
    },
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
});
