import { useMemo, useState } from "react";
import { useAdminAuth } from "../../features/auth";
import { UnwrappedReviewDesk, useUnwrappedReviews } from "../../features/unwrapped";
import { AccountLedger, useAdminUsers, type AccountType } from "../../features/users";
import { Masthead } from "../../shared/components/Masthead";
import "./users-page.css";

type TypeFilter = "ALL" | AccountType;
type ActivityFilter = "ALL" | "ACTIVE" | "INACTIVE";
type AdminSection = "ACCOUNTS" | "UNWRAPPED";

export function UsersPage() {
  const { identity, logout } = useAdminAuth();
  const { users, error, savingUserIds, load, update } = useAdminUsers();
  const unwrapped = useUnwrappedReviews();
  const [section, setSection] = useState<AdminSection>("ACCOUNTS");
  const [query, setQuery] = useState("");
  const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
  const [activityFilter, setActivityFilter] = useState<ActivityFilter>("ALL");

  const filteredUsers = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return (users ?? []).filter((user) => {
      const matchesQuery = !normalizedQuery
        || `${user.displayName} ${user.firstName} ${user.lastName} ${user.email}`
          .toLowerCase()
          .includes(normalizedQuery);
      const matchesType = typeFilter === "ALL" || user.accountType === typeFilter;
      const matchesActivity = activityFilter === "ALL"
        || (activityFilter === "ACTIVE" ? user.active : !user.active);
      return matchesQuery && matchesType && matchesActivity;
    });
  }, [activityFilter, query, typeFilter, users]);

  const counts = useMemo(() => ({
    all: users?.length ?? 0,
    official: users?.filter((user) => user.accountType === "OFFICIAL").length ?? 0,
    admin: users?.filter((user) => user.accountType === "ADMIN").length ?? 0,
    inactive: users?.filter((user) => !user.active).length ?? 0,
  }), [users]);

  if (error?.status === 403 && users === null) {
    return (
      <main className="access-denied">
        <p className="access-denied__eyebrow">Administration · restricted</p>
        <h1>This desk is for active admins.</h1>
        <p>Your identity is valid, but this account does not have active administrator access.</p>
        <button type="button" onClick={() => void logout()}>Sign out</button>
      </main>
    );
  }

  return (
    <div className="accounts-shell">
      <Masthead email={identity?.email ?? "Authenticated"} onLogout={() => void logout()} />

      <nav className="admin-tabs" aria-label="Administration sections" role="tablist">
        <button
          type="button"
          id="accounts-tab"
          role="tab"
          aria-controls="accounts-panel"
          aria-selected={section === "ACCOUNTS"}
          className={section === "ACCOUNTS" ? "admin-tab admin-tab--active" : "admin-tab"}
          onClick={() => setSection("ACCOUNTS")}
        >
          <span>Accounts</span>
          <small>People &amp; permissions</small>
        </button>
        <button
          type="button"
          id="unwrapped-tab"
          role="tab"
          aria-controls="unwrapped-panel"
          aria-selected={section === "UNWRAPPED"}
          className={section === "UNWRAPPED" ? "admin-tab admin-tab--active" : "admin-tab"}
          onClick={() => setSection("UNWRAPPED")}
        >
          <span>
            Unwrapped
            <strong aria-label={`${unwrapped.reviews?.length ?? 0} drafts awaiting review`}>
              {unwrapped.reviews?.length ?? "—"}
            </strong>
          </span>
          <small>Publication approvals</small>
        </button>
      </nav>

      {section === "ACCOUNTS" ? <main
        id="accounts-panel"
        className="accounts-page"
        role="tabpanel"
        aria-labelledby="accounts-tab"
      >
        <header className="accounts-page__intro">
          <div>
            <p className="accounts-page__eyebrow">People &amp; permissions</p>
            <h1>Accounts desk</h1>
          </div>
          <p className="accounts-page__standfirst">
            Decide who reads, who publishes, and who can administer Your Say News.
          </p>
        </header>

        <dl className="account-totals" aria-label="Account totals">
          <div><dt>All</dt><dd>{counts.all}</dd></div>
          <div><dt>Official</dt><dd>{counts.official}</dd></div>
          <div><dt>Admins</dt><dd>{counts.admin}</dd></div>
          <div><dt>Inactive</dt><dd>{counts.inactive}</dd></div>
        </dl>

        <section className="account-tools" aria-label="Account filters">
          <label className="account-search">
            <span>Find an account</span>
            <input
              type="search"
              value={query}
              placeholder="Name or email"
              onChange={(event) => setQuery(event.target.value)}
            />
          </label>
          <label>
            <span>Type</span>
            <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value as TypeFilter)}>
              <option value="ALL">All types</option>
              <option value="USER">Users</option>
              <option value="OFFICIAL">Official posters</option>
              <option value="ADMIN">Admins</option>
            </select>
          </label>
          <label>
            <span>Status</span>
            <select
              value={activityFilter}
              onChange={(event) => setActivityFilter(event.target.value as ActivityFilter)}
            >
              <option value="ALL">Any status</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
            </select>
          </label>
        </section>

        {error ? (
          <div className="account-error" role="alert">
            <span>{error.message}</span>
            <button type="button" onClick={() => void load()}>Reload accounts</button>
          </div>
        ) : null}

        {users === null ? (
          <div className="account-loading" aria-live="polite">Reading the account ledger…</div>
        ) : (
          <>
            <p className="account-results">
              Showing {filteredUsers.length} of {users.length} accounts
            </p>
            <AccountLedger
              users={filteredUsers}
              currentEmail={identity?.email ?? ""}
              savingUserIds={savingUserIds}
              onSave={update}
            />
          </>
        )}
      </main> : (
        <div id="unwrapped-panel" role="tabpanel" aria-labelledby="unwrapped-tab">
          <UnwrappedReviewDesk
            reviews={unwrapped.reviews}
            posts={unwrapped.posts}
            postsError={unwrapped.postsError}
            error={unwrapped.error}
            actingStoryId={unwrapped.actingStoryId}
            generatingPostId={unwrapped.generatingPostId}
            generationError={unwrapped.generationError}
            generationMonitor={unwrapped.generationMonitor}
            onReload={unwrapped.load}
            onReloadPosts={unwrapped.loadPosts}
            onApprove={unwrapped.approve}
            onReject={unwrapped.reject}
            onGenerate={unwrapped.generate}
          />
        </div>
      )}
    </div>
  );
}
