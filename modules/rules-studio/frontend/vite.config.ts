import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";

export default defineConfig({
  plugins: [react()],
  base: "/",
  resolve: {
    alias: {
      "@testing-library/react": path.resolve(__dirname, "src/stubs/testing-library-react.ts")
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://127.0.0.1:8080"
    }
  },
  build: {
    outDir: "../src/main/resources/web",
    emptyOutDir: true,
    sourcemap: false
  }
});
