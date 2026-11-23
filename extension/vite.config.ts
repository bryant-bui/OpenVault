import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: "./",
  build: {
    outDir: "dist",
    emptyOutDir: true,
    target: "esnext",
    rollupOptions: {
      input: {
        popup: path.resolve(import.meta.dirname, "popup.html"),
        sidepanel: path.resolve(import.meta.dirname, "sidepanel.html"),
      },
    },
  },
});
