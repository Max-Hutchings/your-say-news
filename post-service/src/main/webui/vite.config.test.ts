import { describe, expect, it } from "vitest";
import { createAdminViteConfig } from "./vite.config";

describe("admin development routing", () => {
  it("keeps the API proxy outside the admin SPA root", () => {
    const config = createAdminViteConfig();

    expect(config.base).toBe("/admin/");
    expect(config.server?.proxy).toEqual({
      "/api/admin/users": "http://localhost:8082",
      "/api/admin/unwrapped": "http://localhost:8082",
    });
    expect(Object.keys(config.server?.proxy ?? {})).not.toContain("/admin/users");
  });

  it("can target an isolated backend for smoke tests", () => {
    const config = createAdminViteConfig("http://localhost:58082", 58083);

    expect(config.server.proxy).toEqual({
      "/api/admin/users": "http://localhost:58082",
      "/api/admin/unwrapped": "http://localhost:58082",
    });
    expect(config.server.port).toBe(58083);
  });
});
