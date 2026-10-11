import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const firebase = vi.hoisted(() => ({
  initializeApp: vi.fn(() => ({})),
  inMemoryPersistence: { type: "NONE" },
  setPersistence: vi.fn(),
  connectAuthEmulator: vi.fn(),
  getIdToken: vi.fn(),
  signInWithEmailAndPassword: vi.fn(),
  signInWithPopup: vi.fn(),
  signOut: vi.fn(),
}));

vi.mock("firebase/app", () => ({ initializeApp: firebase.initializeApp }));
vi.mock("firebase/auth", () => ({
  connectAuthEmulator: firebase.connectAuthEmulator,
  getAuth: vi.fn(() => ({})),
  GoogleAuthProvider: class GoogleAuthProvider {},
  inMemoryPersistence: firebase.inMemoryPersistence,
  setPersistence: firebase.setPersistence,
  signInWithEmailAndPassword: firebase.signInWithEmailAndPassword,
  signInWithPopup: firebase.signInWithPopup,
  signOut: firebase.signOut,
}));

const laptopConfig = {
  projectId: "demo-your-say-news",
  authDomain: "demo-your-say-news.firebaseapp.com",
  apiKey: "local-emulator-key",
  appId: "local-admin-app",
  signInMethod: "PASSWORD",
  authEmulatorUrl: "http://localhost:9099",
};

const devConfig = {
  projectId: "your-say-news-development",
  authDomain: "your-say-news-development.firebaseapp.com",
  apiKey: "dev-web-api-key",
  appId: "1:123:web:abc",
  signInMethod: "GOOGLE",
  authEmulatorUrl: null,
};

const admin = { email: "admin@yoursay.com", name: "YourSay Admin" };

/**
 * Answers each backend route by its exact URL under the given prefix ("" on a laptop, "/api" when
 * hosted), so the assertions do not depend on call order. Any other URL is a 404, so a call made
 * without the environment's prefix fails the test.
 */
function backend(prefix: string, config: object, responses: Record<string, Response> = {}) {
  const routes: Record<string, () => Response> = {
    "/api/auth/admin/config": () => Response.json(config),
    "/api/auth/admin/csrf": () => Response.json({ token: "csrf-token" }),
    "/api/auth/admin/session": () => Response.json(admin),
    "/api/auth/admin/logout": () => new Response(null, { status: 204 }),
    "/api/admin/users": () => Response.json([]),
    "/api/admin/users/11": () => Response.json({ id: 11 }),
  };
  const fetchMock = vi.fn(async (url: string) => {
    const path = url.startsWith(`${prefix}/api/`) ? url.slice(prefix.length) : null;
    if (path && responses[path]) return responses[path];
    if (path && routes[path]) return routes[path]();
    return new Response(null, { status: 404 });
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

async function loadSessionModule() {
  vi.resetModules();
  return import("./adminSession");
}

describe("Firebase admin session exchange", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    firebase.getIdToken.mockResolvedValue("firebase-id-token");
    firebase.signInWithEmailAndPassword.mockResolvedValue({ user: { getIdToken: firebase.getIdToken } });
    firebase.signInWithPopup.mockResolvedValue({ user: { getIdToken: firebase.getIdToken } });
    firebase.signOut.mockResolvedValue(undefined);
    firebase.setPersistence.mockResolvedValue(undefined);
  });

  afterEach(() => {
    window.history.replaceState({}, "", "/");
  });

  it("on a laptop, signs in through the emulator and calls the API at the root", async () => {
    window.history.replaceState({}, "", "/admin/");
    const fetchMock = backend("", laptopConfig);
    const { adminFetch, loginAdmin, logoutAdmin } = await loadSessionModule();

    await expect(loginAdmin("admin@yoursay.com", "password123")).resolves.toEqual(admin);

    expect(firebase.connectAuthEmulator).toHaveBeenCalledWith(
      expect.anything(), "http://localhost:9099", { disableWarnings: true });
    expect(firebase.setPersistence).toHaveBeenCalledWith(expect.anything(), firebase.inMemoryPersistence);
    expect(firebase.signInWithEmailAndPassword).toHaveBeenCalledWith(
      expect.anything(), "admin@yoursay.com", "password123");
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/admin/session", expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: expect.objectContaining({ "X-CSRF-Token": "csrf-token" }),
      body: JSON.stringify({ idToken: "firebase-id-token" }),
    }));
    expect(firebase.signOut).toHaveBeenCalledTimes(1);

    await adminFetch("/api/admin/users/11", { method: "PUT" });
    expect(fetchMock).toHaveBeenCalledWith("/api/admin/users/11", expect.objectContaining({
      credentials: "include",
      headers: expect.objectContaining({ "X-CSRF-Token": "csrf-token" }),
    }));

    await expect(logoutAdmin()).resolves.toBeUndefined();
  });

  it("on dev, signs in with Google against the real project and prefixes every call with /api", async () => {
    window.history.replaceState({}, "", "/api/admin/");
    const fetchMock = backend("/api", devConfig);
    const { adminFetch, loadAdminWebConfig, loginAdminWithGoogle, logoutAdmin } = await loadSessionModule();

    await expect(loadAdminWebConfig()).resolves.toMatchObject({ signInMethod: "GOOGLE" });
    await expect(loginAdminWithGoogle()).resolves.toEqual(admin);

    expect(firebase.initializeApp).toHaveBeenCalledWith({
      apiKey: "dev-web-api-key",
      authDomain: "your-say-news-development.firebaseapp.com",
      projectId: "your-say-news-development",
      appId: "1:123:web:abc",
    });
    expect(firebase.connectAuthEmulator).not.toHaveBeenCalled();
    expect(firebase.setPersistence).toHaveBeenCalledWith(expect.anything(), firebase.inMemoryPersistence);
    expect(firebase.signInWithPopup).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/api/auth/admin/config");
    expect(fetchMock).toHaveBeenCalledWith("/api/api/auth/admin/csrf", { credentials: "include" });
    expect(fetchMock).toHaveBeenCalledWith("/api/api/auth/admin/session", expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: expect.objectContaining({ "X-CSRF-Token": "csrf-token" }),
      body: JSON.stringify({ idToken: "firebase-id-token" }),
    }));
    expect(firebase.signOut).toHaveBeenCalledTimes(1);

    const users = await adminFetch("/api/admin/users");
    expect(users.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("/api/api/admin/users", { credentials: "include", headers: {} });

    await expect(logoutAdmin()).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledWith("/api/api/auth/admin/logout", expect.objectContaining({
      method: "POST",
      headers: { "X-CSRF-Token": "csrf-token" },
    }));
  });

  it("reports a Google account without admin access and still discards its Firebase state", async () => {
    window.history.replaceState({}, "", "/api/admin/");
    backend("/api", devConfig, { "/api/auth/admin/session": new Response(null, { status: 403 }) });
    const { loginAdminWithGoogle } = await loadSessionModule();

    await expect(loginAdminWithGoogle()).rejects.toThrow(
      "This account does not have active administrator access.");

    expect(firebase.signOut).toHaveBeenCalledTimes(1);
  });

  it("rejects logout when the backend does not clear the session cookie", async () => {
    window.history.replaceState({}, "", "/admin/");
    const fetchMock = backend("", laptopConfig, { "/api/auth/admin/logout": new Response(null, { status: 500 }) });
    const { logoutAdmin } = await loadSessionModule();

    await expect(logoutAdmin()).rejects.toThrow("The admin session could not be ended.");

    expect(fetchMock).toHaveBeenCalledWith("/api/auth/admin/logout", expect.objectContaining({
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Token": "csrf-token" },
    }));
  });
});
