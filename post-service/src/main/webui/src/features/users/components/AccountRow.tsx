import { useEffect, useState } from "react";
import { AccountTypeSelect } from "./AccountTypeSelect";
import { ActiveToggle } from "./ActiveToggle";
import type { AccountType, AdminUser, AdminUserUpdate } from "../types";

export function AccountRow({
  user,
  currentEmail,
  saving,
  onSave,
}: {
  user: AdminUser;
  currentEmail: string;
  saving: boolean;
  onSave: (userId: number, changes: AdminUserUpdate) => Promise<AdminUser>;
}) {
  const [accountType, setAccountType] = useState<AccountType>(user.accountType);
  const [active, setActive] = useState(user.active);

  useEffect(() => {
    setAccountType(user.accountType);
    setActive(user.active);
  }, [user.accountType, user.active]);

  const dirty = accountType !== user.accountType || active !== user.active;
  const name = user.displayName || `${user.firstName} ${user.lastName}`.trim() || user.email;

  const save = async () => {
    try {
      await onSave(user.id, { accountType, active });
    } catch {
      // The page-level error banner gives the retry guidance; keep edits in place.
    }
  };

  return (
    <li className={`account-row account-row--${active ? "active" : "inactive"}`}>
      <div className="account-row__identity">
        <span className="account-row__initial" aria-hidden="true">{name.charAt(0).toUpperCase()}</span>
        <div>
          <p className="account-row__name">
            {name}
            {user.email === currentEmail ? <span className="account-row__you">You</span> : null}
          </p>
          <p className="account-row__email">{user.email}</p>
        </div>
      </div>

      <p className="account-row__joined">
        <span className="account-control__mobile-label">Joined</span>
        {new Intl.DateTimeFormat("en-GB", {
          day: "2-digit",
          month: "short",
          year: "numeric",
        }).format(new Date(`${user.createdDate}T00:00:00`))}
      </p>

      <AccountTypeSelect
        name={name}
        value={accountType}
        disabled={saving}
        onChange={setAccountType}
      />

      <ActiveToggle
        name={name}
        active={active}
        disabled={saving}
        onChange={setActive}
      />

      <button
        className="account-row__save"
        type="button"
        disabled={!dirty || saving}
        onClick={() => void save()}
      >
        {saving ? "Saving…" : dirty ? "Save changes" : "Saved"}
      </button>
    </li>
  );
}
