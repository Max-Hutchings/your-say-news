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
  const { status, error } = useAdminAuth();

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

  return <UsersPage />;
}
