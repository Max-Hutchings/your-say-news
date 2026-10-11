import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";

type ViteCommand = "serve" | "build";

export function createAdminViteConfig(
  adminApiOrigin = "http://localhost:8082",
  adminPort = 8083,
  command: ViteCommand = "serve"
) {
  return {
    // The dev server sits behind Quinoa at /admin/. The built bundle is served at /admin/ in tests
    // and /api/admin/ when hosted, so its asset URLs are relative to the page (ADR-064).
    base: command === "build" ? "./" : "/admin/",
    plugins: [react()],
    server: {
      host: "localhost",
      port: adminPort,
      strictPort: true,
      proxy: {
        "/api/admin": adminApiOrigin,
        "/api/auth": adminApiOrigin,
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
    },
  };
}

type EnvironmentLoader = typeof loadEnv;

export function resolveAdminViteConfig(
  { mode, command = "serve" }: { mode: string; command?: ViteCommand },
  environmentLoader: EnvironmentLoader = loadEnv
) {
  const environment = environmentLoader(mode, ".", "");
  const adminPort = Number(environment.VITE_ADMIN_PORT ?? 8083);
  return createAdminViteConfig(environment.VITE_API_ORIGIN, adminPort, command);
}

export default defineConfig(resolveAdminViteConfig);
