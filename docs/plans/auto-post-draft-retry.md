# Auto-post draft retry

## Goal

Let an administrator retry a failed post-agent draft without repeating story discovery.

## Contract

- Retry is available only when a selected story's post-agent draft failed.
- The post agent creates a new job from the failed job's persisted prompt, preserving the exact input.
- The auto-post run points at the new job and returns to `DRAFTING`.
- Discovery failures and non-failed runs return a conflict.
- The admin ledger shows the action and live-streams the replacement job's result.

## Verification

- Integration-test exact prompt reuse, workflow state, authorization, and invalid retries.
- Test the admin API, hook, and failed-row action.
- Record retry outcomes in auto-post telemetry and the posts dashboard.
