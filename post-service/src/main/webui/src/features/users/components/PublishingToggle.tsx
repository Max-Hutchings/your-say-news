import type { AccountType } from "../types";

/** Publishing permission (ADR-066). USER accounts can never post, so the switch is locked off. */
export function PublishingToggle({
  name,
  accountType,
  publishingEnabled,
  disabled,
  onChange,
}: {
  name: string;
  accountType: AccountType;
  publishingEnabled: boolean;
  disabled: boolean;
  onChange: (publishingEnabled: boolean) => void;
}) {
  const locked = accountType === "USER";

  return (
    <label className="account-toggle">
      <span className="account-control__mobile-label">Publishing</span>
      <input
        type="checkbox"
        role="switch"
        aria-label={`Publishing for ${name}`}
        checked={publishingEnabled && !locked}
        disabled={disabled || locked}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="account-toggle__track" aria-hidden="true">
        <span />
      </span>
      <span>{publishingEnabled && !locked ? "Can post" : "Can't post"}</span>
    </label>
  );
}
