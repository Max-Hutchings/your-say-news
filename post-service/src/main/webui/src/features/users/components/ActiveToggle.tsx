export function ActiveToggle({
  name,
  active,
  disabled,
  onChange,
}: {
  name: string;
  active: boolean;
  disabled: boolean;
  onChange: (active: boolean) => void;
}) {
  return (
    <label className="account-toggle">
      <input
        type="checkbox"
        role="switch"
        aria-label={`Account active for ${name}`}
        checked={active}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="account-toggle__track" aria-hidden="true">
        <span />
      </span>
      <span>{active ? "Active" : "Inactive"}</span>
    </label>
  );
}
