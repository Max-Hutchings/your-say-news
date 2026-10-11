import { initializeApp } from "firebase/app";
import {
  type Auth,
  type UserCredential,
  connectAuthEmulator,
  getAuth,
  GoogleAuthProvider,
  inMemoryPersistence,
  setPersistence,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
} from "firebase/auth";
import type { AdminIdentity, AdminWebConfig } from "../types";

let webConfig: Promise<AdminWebConfig> | null = null;
let firebaseAuth: Promise<Auth> | null = null;
let csrfToken: string | null = null;

/**
 * The SPA is served at /admin/ on a laptop and /api/admin/ when hosted, where the whole backend
 * sits below the /api root path (ADR-036). Admin API calls take the same prefix as the page.
 */
function apiPath(path: string): string {
  const pathname = window.location.pathname;
  const adminRoot = pathname.indexOf("/admin");
  return (adminRoot > 0 ? pathname.slice(0, adminRoot) : "") + path;
}

/** Firebase web settings differ per environment, so the backend serves them at runtime (ADR-064). */
export function loadAdminWebConfig(): Promise<AdminWebConfig> {
  webConfig ??= fetch(apiPath("/api/auth/admin/config")).then(async (response) => {
    if (!response.ok) throw new Error("The admin sign-in settings could not be loaded.");
    return response.json() as Promise<AdminWebConfig>;
  });
  return webConfig;
}

function getFirebaseAuth(): Promise<Auth> {
  firebaseAuth ??= loadAdminWebConfig().then((config) => {
    const auth = getAuth(initializeApp({
      apiKey: config.apiKey,
      authDomain: config.authDomain,
      projectId: config.projectId,
      appId: config.appId,
    }));
    if (config.authEmulatorUrl) {
      connectAuthEmulator(auth, config.authEmulatorUrl, { disableWarnings: true });
    }
    return auth;
  });
  return firebaseAuth;
}

export async function initializeAdminSession(): Promise<AdminIdentity | null> {
  const response = await fetch(apiPath("/api/auth/admin/session"), { credentials: "include" });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error("The admin session could not be checked.");
  return response.json() as Promise<AdminIdentity>;
}

export async function loginAdmin(email: string, password: string): Promise<AdminIdentity> {
  return exchangeFirebaseSignIn((auth) => signInWithEmailAndPassword(auth, email, password));
}

export async function loginAdminWithGoogle(): Promise<AdminIdentity> {
  return exchangeFirebaseSignIn((auth) => signInWithPopup(auth, new GoogleAuthProvider()));
}

/**
 * Trades a fresh Firebase sign-in for the backend's HttpOnly session cookie, then signs out of
 * Firebase so the browser keeps no Firebase credential of its own.
 */
async function exchangeFirebaseSignIn(
  signIn: (auth: Auth) => Promise<UserCredential>,
): Promise<AdminIdentity> {
  const auth = await getFirebaseAuth();
  await setPersistence(auth, inMemoryPersistence);
  const credential = await signIn(auth);
  try {
    const token = await credential.user.getIdToken();
    const response = await fetch(apiPath("/api/auth/admin/session"), {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": await getAdminCsrfToken(),
      },
      body: JSON.stringify({ idToken: token }),
    });
    if (!response.ok) {
      throw new Error(response.status === 403
        ? "This account does not have active administrator access."
        : "The admin session could not be created.");
    }
    return response.json() as Promise<AdminIdentity>;
  } finally {
    await signOut(auth);
  }
}

export async function getAdminCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  const response = await fetch(apiPath("/api/auth/admin/csrf"), { credentials: "include" });
  if (!response.ok) throw new Error("The admin security token could not be created.");
  const body = await response.json() as { token: string };
  csrfToken = body.token;
  return body.token;
}

export async function logoutAdmin(): Promise<void> {
  const response = await fetch(apiPath("/api/auth/admin/logout"), {
    method: "POST",
    credentials: "include",
    headers: { "X-CSRF-Token": await getAdminCsrfToken() },
  });
  if (!response.ok) throw new Error("The admin session could not be ended.");
  csrfToken = null;
}

export async function adminFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const mutation = !["GET", "HEAD", "OPTIONS"].includes(method);
  return fetch(apiPath(path), {
    ...init,
    credentials: "include",
    headers: {
      ...init.headers,
      ...(mutation ? { "X-CSRF-Token": await getAdminCsrfToken() } : {}),
    },
  });
}
