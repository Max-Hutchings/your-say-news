import { afterEach, describe, expect, it, vi } from "vitest";
import adminViteConfig, {
  createAdminViteConfig,
  resolveAdminViteConfig,
} from "./vite.config";

function configurationContract(config: ReturnType<typeof createAdminViteConfig>) {
  return {
    base: config.base,
    pluginNames: config.plugins.flat().map((plugin) => plugin.name),
    server: config.server,
    test: config.test,
  };
}

describe("admin development routing", () => {
  afterEach(() => vi.unstubAllEnvs());

  it("keeps the API proxy outside the admin SPA root", () => {
    const config = createAdminViteConfig();

    expect(Object.keys(config).sort()).toEqual(["base", "plugins", "server", "test"]);
    expect(config.base).toBe("/admin/");
    expect(Object.keys(config.server).sort()).toEqual([
      "host",
      "port",
      "proxy",
      "strictPort",
    ]);
    expect(config.server?.proxy).toEqual({
      "/api/admin": "http://localhost:8082",
    });
    expect(config.server.host).toBe("localhost");
    expect(config.server.port).toBe(8083);
    expect(config.server.strictPort).toBe(true);
    expect(config.test).toEqual({
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
    });
    expect(config.plugins.flat().map((plugin) => plugin.name)).toEqual([
      "vite:react-babel",
      "vite:react:refresh-wrapper",
      "vite:react:config-post",
      "vite:react-refresh-fbm",
      "vite:react-refresh",
      "vite:react-virtual-preamble",
    ]);
  });

  it("loads smoke-test environment overrides through the exported Vite resolver", () => {
    const loadEnvironment = vi.fn(() => ({
      VITE_API_ORIGIN: "http://localhost:58082",
      VITE_ADMIN_PORT: "58083",
    }));
    const config = resolveAdminViteConfig({ mode: "smoke" }, loadEnvironment);

    expect(adminViteConfig).toBe(resolveAdminViteConfig);
    expect(loadEnvironment).toHaveBeenCalledWith("smoke", ".", "");
    expect(config.server.proxy).toEqual({
      "/api/admin": "http://localhost:58082",
    });
    expect(config.server.port).toBe(58083);
    expect(configurationContract(config)).toEqual(
      configurationContract(createAdminViteConfig("http://localhost:58082", 58083))
    );
  });

  it("loads production environment overrides through Vite loadEnv", () => {
    vi.stubEnv("VITE_API_ORIGIN", "http://localhost:58084");
    vi.stubEnv("VITE_ADMIN_PORT", "58085");

    const config = resolveAdminViteConfig({ mode: "development" });

    expect(config.server.proxy).toEqual({
      "/api/admin": "http://localhost:58084",
    });
    expect(config.server.port).toBe(58085);
  });

  it("uses local development defaults when environment overrides are absent", () => {
    const config = resolveAdminViteConfig({ mode: "development" }, () => ({}));

    expect(configurationContract(config)).toEqual(configurationContract(createAdminViteConfig()));
  });
});
