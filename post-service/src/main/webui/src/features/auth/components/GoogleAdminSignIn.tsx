/** Hosted sign-in: a Google popup against the environment's Firebase project (ADR-064). */
export function GoogleAdminSignIn({
  error,
  loginWithGoogle,
}: {
  error: string | null;
  loginWithGoogle: () => Promise<void>;
}) {
  return (
    <main className="session-state">
      <span className="session-state__mark" aria-hidden="true">Y</span>
      <h1>Admin sign-in</h1>
      <p>Sign in with the Google account that has administrator access.</p>
      <button type="button" onClick={() => void loginWithGoogle()}>Sign in with Google</button>
      {error ? <p role="alert">{error}</p> : null}
    </main>
  );
}
