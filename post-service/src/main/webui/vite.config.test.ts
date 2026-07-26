import { describe, expect, it } from "vitest";
import viteConfig from "./vite.config";

describe("admin development routing", () => {
  it("keeps the API proxy outside the admin SPA root", () => {
    const config = viteConfig as {
      base?: string;
      server?: { proxy?: Record<string, string> };
    };

    expect(config.base).toBe("/admin/");
    expect(config.server?.proxy).toEqual({
      "/api/admin/users": "http://localhost:8082",
    });
    expect(Object.keys(config.server?.proxy ?? {})).not.toContain("/admin/users");
  });
});
