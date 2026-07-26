import type { AccountType } from "../types";

const options: { value: AccountType; label: string }[] = [
  { value: "USER", label: "User" },
  { value: "OFFICIAL", label: "Official poster" },
  { value: "ADMIN", label: "Admin" },
];

export function AccountTypeSelect({
  name,
  value,
  disabled,
  onChange,
}: {
  name: string;
  value: AccountType;
  disabled: boolean;
  onChange: (value: AccountType) => void;
}) {
  return (
    <label className="account-control">
      <span className="account-control__mobile-label">Account type</span>
      <select
        aria-label={`Account type for ${name}`}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value as AccountType)}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    </label>
  );
}
