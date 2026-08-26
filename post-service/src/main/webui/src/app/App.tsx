import type { FormEvent } from "react";
import { AuthProvider, useAdminAuth } from "../features/auth";
import { UsersPage } from "../pages/users";

export function App() {
  return (
    <AuthProvider>
      <AuthenticatedApplication />
    </AuthProvider>
  );
}

function AuthenticatedApplication() {
  const { status, error, login } = useAdminAuth();

  if (status === "loading") {
    return (
      <main className="session-state" aria-live="polite">
        <span className="session-state__mark" aria-hidden="true">Y</span>
        <p>Opening the admin desk…</p>
      </main>
    );
  }

  if (status === "error") {
    return (
      <main className="session-state" role="alert">
        <span className="session-state__mark" aria-hidden="true">!</span>
        <h1>Sign-in could not be completed</h1>
        <p>{error ?? "Check the identity service and reload this page."}</p>
        <button type="button" onClick={() => window.location.reload()}>Try again</button>
      </main>
    );
  }

  if (status === "unauthenticated") {
    return <LocalAdminSignIn error={error} login={login} />;
  }

  return <UsersPage />;
}

function LocalAdminSignIn({
  error,
  login,
}: {
  error: string | null;
  login: (email: string, password: string) => Promise<void>;
}) {
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    void login(String(data.get("email")), String(data.get("password")));
  };

  return (
    <main className="session-state">
      <span className="session-state__mark" aria-hidden="true">Y</span>
      <h1>Admin sign-in</h1>
      <p>Use a seeded Firebase Emulator test account.</p>
      <form className="session-form" onSubmit={submit}>
        <label>Email<input name="email" type="email" defaultValue="admin@yoursay.com" /></label>
        <label>Password<input name="password" type="password" defaultValue="password123" /></label>
        <button type="submit">Sign in</button>
      </form>
      {error ? <p role="alert">{error}</p> : null}
    </main>
  );
}
