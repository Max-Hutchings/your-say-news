export type AdminIdentity = {
  email: string;
  name: string;
};

export type AdminAuthState = {
  status: "loading" | "unauthenticated" | "authenticated" | "error";
  identity: AdminIdentity | null;
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
};
