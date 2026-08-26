import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { initializeAdminSession, loginAdmin, logoutAdmin } from "../services/adminSession";
import type { AdminAuthState, AdminIdentity } from "../types";

const AdminAuthContext = createContext<AdminAuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AdminAuthState["status"]>("loading");
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    initializeAdminSession()
      .then((sessionIdentity) => {
        if (!mounted) {
          return;
        }
        if (!sessionIdentity) {
          setStatus("unauthenticated");
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

  const login = async (email: string, password: string) => {
    setError(null);
    try {
      const sessionIdentity = await loginAdmin(email, password);
      setIdentity(sessionIdentity);
      setStatus("authenticated");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Sign-in failed.");
      setStatus("unauthenticated");
    }
  };

  const logout = async () => {
    await logoutAdmin();
    setIdentity(null);
    setStatus("unauthenticated");
  };

  const value = useMemo<AdminAuthState>(() => ({
    status,
    identity,
    error,
    login,
    logout,
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
