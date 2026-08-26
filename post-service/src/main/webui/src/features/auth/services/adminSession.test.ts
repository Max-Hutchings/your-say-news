import { beforeEach, describe, expect, it, vi } from "vitest";

const { getIdToken, signOut } = vi.hoisted(() => ({
  getIdToken: vi.fn().mockResolvedValue("firebase-id-token"),
  signOut: vi.fn().mockResolvedValue(undefined),
}));

vi.mock("firebase/app", () => ({ initializeApp: vi.fn(() => ({})) }));
vi.mock("firebase/auth", () => ({
  connectAuthEmulator: vi.fn(),
  getAuth: vi.fn(() => ({})),
  inMemoryPersistence: {},
  setPersistence: vi.fn().mockResolvedValue(undefined),
  signInWithEmailAndPassword: vi.fn().mockResolvedValue({ user: { getIdToken } }),
  signOut,
}));

import { adminFetch, loginAdmin, logoutAdmin } from "./adminSession";

describe("Firebase admin session exchange", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("exchanges the Firebase token for a cookie session then discards Firebase state", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        email: "admin@yoursay.com",
        name: "YourSay Admin",
      }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(loginAdmin("admin@yoursay.com", "password123")).resolves.toEqual({
      email: "admin@yoursay.com",
      name: "YourSay Admin",
    });

    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/auth/admin/session", expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: expect.objectContaining({ "X-CSRF-Token": "csrf-token" }),
      body: JSON.stringify({ idToken: "firebase-id-token" }),
    }));
    expect(signOut).toHaveBeenCalledTimes(1);

    await adminFetch("/api/admin/users/11", { method: "PUT" });

    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/admin/users/11", expect.objectContaining({
      credentials: "include",
      headers: expect.objectContaining({ "X-CSRF-Token": "csrf-token" }),
    }));

    await expect(logoutAdmin()).resolves.toBeUndefined();
  });

  it("rejects logout when the backend does not clear the session cookie", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 500 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(logoutAdmin()).rejects.toThrow("The admin session could not be ended.");

    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/auth/admin/logout", expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Token": "csrf-token" },
    }));
  });
});
