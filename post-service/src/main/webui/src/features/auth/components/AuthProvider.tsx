import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  initializeAdminSession,
  loadAdminWebConfig,
  loginAdmin,
  loginAdminWithGoogle,
  logoutAdmin,
} from "../services/adminSession";
import type { AdminAuthState, AdminIdentity, AdminSignInMethod } from "../types";

const AdminAuthContext = createContext<AdminAuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AdminAuthState["status"]>("loading");
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [signInMethod, setSignInMethod] = useState<AdminSignInMethod | null>(null);

  useEffect(() => {
    let mounted = true;

    Promise.all([initializeAdminSession(), loadAdminWebConfig()])
      .then(([sessionIdentity, webConfig]) => {
        if (!mounted) {
          return;
        }
        setSignInMethod(webConfig.signInMethod);
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

  const completeSignIn = async (signIn: () => Promise<AdminIdentity>) => {
    setError(null);
    try {
      const sessionIdentity = await signIn();
      setIdentity(sessionIdentity);
      setStatus("authenticated");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Sign-in failed.");
      setStatus("unauthenticated");
    }
  };

  const login = (email: string, password: string) => completeSignIn(() => loginAdmin(email, password));
  const loginWithGoogle = () => completeSignIn(loginAdminWithGoogle);

  const logout = async () => {
    await logoutAdmin();
    setIdentity(null);
    setStatus("unauthenticated");
  };

  const value = useMemo<AdminAuthState>(() => ({
    status,
    identity,
    error,
    signInMethod,
    login,
    loginWithGoogle,
    logout,
  }), [error, identity, signInMethod, status]);

  return <AdminAuthContext.Provider value={value}>{children}</AdminAuthContext.Provider>;
}

export function useAdminAuth(): AdminAuthState {
  const value = useContext(AdminAuthContext);
  if (!value) {
    throw new Error("useAdminAuth must be used inside AuthProvider.");
  }
  return value;
}
