import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("../../auth", () => ({
  adminFetch: vi.fn((path, init) => fetch(path, init)),
}));

import { AdminApiError, getAdminUsers, updateAdminUser } from "./userAdminApi";

describe("userAdminApi", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("lists accounts using the shared admin request boundary", async () => {
    const users = [{
      id: 10,
      email: "riley.reader@example.com",
      firstName: "Riley",
      lastName: "Reader",
      displayName: "Riley Reader",
      createdDate: "2024-06-06",
      active: true,
      accountType: "USER",
    }];
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(users), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getAdminUsers()).resolves.toEqual(users);
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/users", expect.objectContaining({
      headers: expect.objectContaining({ Accept: "application/json" }),
    }));
  });

  it("sends the complete account state when an admin saves changes", async () => {
    const saved = {
      id: 10,
      email: "riley.reader@example.com",
      firstName: "Riley",
      lastName: "Reader",
      displayName: "Riley Reader",
      createdDate: "2024-06-06",
      active: false,
      accountType: "ADMIN",
    };
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(saved), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(updateAdminUser(10, {
      accountType: "ADMIN",
      active: false,
    })).resolves.toEqual(saved);
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/users/10", expect.objectContaining({
      method: "PUT",
      body: JSON.stringify({ accountType: "ADMIN", active: false }),
      headers: expect.objectContaining({ "Content-Type": "application/json" }),
    }));
  });

  it("preserves the response status and safe API message on failure", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: "USER_ADMIN_ACCESS_REQUIRED",
      message: "You are not allowed to perform this action.",
    }), {
      status: 403,
      headers: { "Content-Type": "application/json" },
    })));

    await expect(getAdminUsers()).rejects.toEqual(
      new AdminApiError(403, "You are not allowed to perform this action."),
    );
  });
});
