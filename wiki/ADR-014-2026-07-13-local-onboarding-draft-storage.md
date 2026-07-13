# ADR-014: Local onboarding draft storage

## Situation

Characteristic onboarding is long enough that users can leave after a completed page. A partial
profile must be recoverable, but it must not be treated as reportable characteristic data or make a
user eligible for aggregate voting breakdowns.

## Options considered

1. Save each partial page to the characteristic API.
2. Keep the in-progress form only in memory.
3. Save an authenticated-user-scoped draft locally, then submit the full profile once complete.

## Decision

Use option 3. Completed wizard pages are saved locally under the signed-in user's internal id. Native
clients use SecureStore and the web client uses browser-local storage. The draft is restored on the
next visit, is cleared only after successful full submission, and is never sent to the characteristic
API before that submission.

## Reason

The existing API validates a complete profile and is the source used for anonymised aggregation.
Keeping incomplete answers out of it preserves the distinction between a resumable draft and a valid,
reportable profile without changing onboarding or voting eligibility.

## Consequences

- A draft resumes only on the same device/browser and signed-in account.
- Draft answers remain local until final submission; incomplete data cannot enter aggregate reports.
- The wizard saves progress after every Continue action and keeps the final snapshot if submission
  fails, so a retry does not lose answers.
