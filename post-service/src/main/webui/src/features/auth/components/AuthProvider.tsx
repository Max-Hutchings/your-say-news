import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { currentIdentity, initializeAdminSession, logoutAdmin } from "../services/keycloak";
import type { AdminAuthState, AdminIdentity } from "../types";

const AdminAuthContext = createContext<AdminAuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AdminAuthState["status"]>("loading");
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    initializeAdminSession()
      .then((authenticated) => {
        if (!mounted) {
          return;
        }
        const sessionIdentity = authenticated ? currentIdentity() : null;
        if (!sessionIdentity) {
          setError("The identity token did not include an email address.");
          setStatus("error");
          return;
        }
        setIdentity(sessionIdentity);
        setStatus("authenticated");
      })
      .catch((reason: unknown) => {
        if (!mounted) {
          return;
        }
        setError(reason instanceof Error ? reason.message : "The identity service did not respond.");
        setStatus("error");
      });

    return () => {
      mounted = false;
    };
  }, []);

  const value = useMemo<AdminAuthState>(() => ({
    status,
    identity,
    error,
    logout: logoutAdmin,
  }), [error, identity, status]);

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}

export function useAdminAuth(): AdminAuthState {
  const value = useContext(AdminAuthContext);
  if (!value) {
    throw new Error("useAdminAuth must be used inside AuthProvider.");
  }
  return value;
}
