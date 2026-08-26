import { initializeApp } from "firebase/app";
import {
  connectAuthEmulator,
  getAuth,
  inMemoryPersistence,
  setPersistence,
  signInWithEmailAndPassword,
  signOut,
} from "firebase/auth";
import type { AdminIdentity } from "../types";

const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID ?? "demo-your-say-news";
const firebaseApp = initializeApp({
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? "local-emulator-key",
  authDomain: `${projectId}.firebaseapp.com`,
  projectId,
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? "local-admin-app",
});
const firebaseAuth = getAuth(firebaseApp);
connectAuthEmulator(
  firebaseAuth,
  import.meta.env.VITE_FIREBASE_AUTH_EMULATOR_URL ?? "http://localhost:9099",
  { disableWarnings: true },
);

let csrfToken: string | null = null;

export async function initializeAdminSession(): Promise<AdminIdentity | null> {
  const response = await fetch("/api/auth/admin/session", { credentials: "include" });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error("The admin session could not be checked.");
  return response.json() as Promise<AdminIdentity>;
}

export async function loginAdmin(email: string, password: string): Promise<AdminIdentity> {
  await setPersistence(firebaseAuth, inMemoryPersistence);
  const credential = await signInWithEmailAndPassword(firebaseAuth, email, password);
  try {
    const token = await credential.user.getIdToken();
    const response = await fetch("/api/auth/admin/session", {
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
    await signOut(firebaseAuth);
  }
}

export async function getAdminCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  const response = await fetch("/api/auth/admin/csrf", { credentials: "include" });
  if (!response.ok) throw new Error("The admin security token could not be created.");
  const body = await response.json() as { token: string };
  csrfToken = body.token;
  return body.token;
}

export async function logoutAdmin(): Promise<void> {
  const response = await fetch("/api/auth/admin/logout", {
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
  return fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      ...init.headers,
      ...(mutation ? { "X-CSRF-Token": await getAdminCsrfToken() } : {}),
    },
  });
}
