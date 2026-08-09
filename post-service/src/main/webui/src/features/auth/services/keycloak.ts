import Keycloak from "keycloak-js";
import type { AdminIdentity } from "../types";

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080",
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? "your-say-news",
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "admin-client",
});

let initialization: Promise<boolean> | null = null;

export function initializeAdminSession(): Promise<boolean> {
  if (!initialization) {
    initialization = keycloak.init({
      onLoad: "login-required",
      pkceMethod: "S256",
      checkLoginIframe: false,
      redirectUri: `${window.location.origin}/admin/`,
    });
  }
  return initialization;
}

export function currentIdentity(): AdminIdentity | null {
  if (!keycloak.authenticated || !keycloak.tokenParsed) {
    return null;
  }

  const claims = keycloak.tokenParsed as Record<string, unknown>;
  const email = typeof claims.email === "string" ? claims.email : "";
  const name = typeof claims.name === "string"
    ? claims.name
    : typeof claims.preferred_username === "string"
      ? claims.preferred_username
      : email;

  return email ? { email, name } : null;
}

export async function getAccessToken(): Promise<string> {
  if (!keycloak.authenticated) {
    throw new Error("The admin session is not authenticated.");
  }
  await keycloak.updateToken(30);
  if (!keycloak.token) {
    throw new Error("The admin session has no access token.");
  }
  return keycloak.token;
}

export async function logoutAdmin(): Promise<void> {
  await keycloak.logout({ redirectUri: `${window.location.origin}/admin/` });
}
