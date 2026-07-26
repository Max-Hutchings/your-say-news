function formatToday() {
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
    year: "numeric",
  })
    .format(new Date())
    .toUpperCase();
}

type MastheadProps = {
  email: string;
  onLogout: () => void;
};

export function Masthead({ email, onLogout }: MastheadProps) {
  return (
    <header className="masthead">
      <div className="masthead__meta">
        <p>{formatToday()}</p>
        <p>{email}</p>
      </div>

      <div className="masthead__rule">
        <div className="masthead__brand" aria-label="Your Say News">
          <span className="masthead__mark" aria-hidden="true">
            Y
          </span>
          <span>Your Say</span>
          <em>News</em>
        </div>
        <button className="masthead__sign-out" type="button" onClick={onLogout}>
          Sign out
        </button>
      </div>
    </header>
  );
}
