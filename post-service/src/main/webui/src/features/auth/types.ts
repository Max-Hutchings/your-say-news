export type AdminIdentity = {
  email: string;
  name: string;
};

export type AdminAuthState = {
  status: "loading" | "authenticated" | "error";
  identity: AdminIdentity | null;
  error: string | null;
  logout: () => Promise<void>;
};
