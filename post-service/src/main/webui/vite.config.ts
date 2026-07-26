import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  base: "/admin/",
  plugins: [react()],
  server: {
    host: "localhost",
    port: 8083,
    strictPort: true,
    proxy: {
      "/api/admin/users": "http://localhost:8082",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
  },
});
