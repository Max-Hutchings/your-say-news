export type AdminIdentity = {
  email: string;
  name: string;
};

export type AdminSignInMethod = "PASSWORD" | "GOOGLE";

/** Public Firebase web settings served by the backend (AdminWebConfigController). */
export type AdminWebConfig = {
  projectId: string;
  authDomain: string;
  apiKey: string;
  appId: string;
  signInMethod: AdminSignInMethod;
  authEmulatorUrl: string | null;
};

export type AdminAuthState = {
  status: "loading" | "unauthenticated" | "authenticated" | "error";
  identity: AdminIdentity | null;
  error: string | null;
  signInMethod: AdminSignInMethod | null;
  login: (email: string, password: string) => Promise<void>;
  loginWithGoogle: () => Promise<void>;
  logout: () => Promise<void>;
};
