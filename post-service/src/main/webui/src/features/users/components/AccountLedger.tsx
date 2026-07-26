import { AccountRow } from "./AccountRow";
import type { AdminUser, AdminUserUpdate } from "../types";

export function AccountLedger({
  users,
  currentEmail,
  savingUserIds,
  onSave,
}: {
  users: AdminUser[];
  currentEmail: string;
  savingUserIds: Set<number>;
  onSave: (userId: number, changes: AdminUserUpdate) => Promise<AdminUser>;
}) {
  if (users.length === 0) {
    return (
      <div className="account-empty">
        <p>No accounts match these filters.</p>
        <span>Change the search or account filters to see more people.</span>
      </div>
    );
  }

  return (
    <section className="account-ledger" aria-label="User accounts">
      <div className="account-ledger__heading" aria-hidden="true">
        <span>Account</span>
        <span>Joined</span>
        <span>Type</span>
        <span>Status</span>
        <span>Action</span>
      </div>
      <ol>
        {users.map((user) => (
          <AccountRow
            key={user.id}
            user={user}
            currentEmail={currentEmail}
            saving={savingUserIds.has(user.id)}
            onSave={onSave}
          />
        ))}
      </ol>
    </section>
  );
}
