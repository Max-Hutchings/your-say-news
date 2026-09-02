import { defineConfig } from "vitest/config";
import { loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export function createAdminViteConfig(
  adminApiOrigin = "http://localhost:8082",
  adminPort = 8083
) {
  return {
    base: "/admin/",
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
  { mode }: { mode: string },
  environmentLoader: EnvironmentLoader = loadEnv
) {
  const environment = environmentLoader(mode, ".", "");
  const adminPort = Number(environment.VITE_ADMIN_PORT ?? 8083);
  return createAdminViteConfig(environment.VITE_API_ORIGIN, adminPort);
}

export default defineConfig(resolveAdminViteConfig);
