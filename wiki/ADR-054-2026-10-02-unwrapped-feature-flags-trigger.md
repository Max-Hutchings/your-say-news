# ADR-054 - Unwrapped feature flags and voter-triggered generation

## Status

Accepted on 2026-10-02. Extends ADR-036: an administrator is no longer the only trigger for
Unwrapped generation.

## Situation

After a vote the app opened Post Unwrapped straight away. Product wants voters to see the vote
data first, with Unwrapped one tap away. Product also needs a way to turn Unwrapped off
everywhere, including the background generation that costs model calls.

Before this change only `POST /api/admin/unwrapped/posts/{postId}/generate` placed a post into
reconciliation. Casting a vote never queued anything.

## Options considered

1. One flag that only changes the app's navigation.
2. Two flags: a UI flag for the post-vote journey and a kill switch enforced by the backend.
3. Flags in Expo app config, read at build time.
4. Flags in `post-service` config, served to the app by an endpoint.

For the Unwrap button: only open Unwrapped (generation stays admin-only), or also queue generation
from the voter's tap.

## Decision

Choose options 2 and 4, and let the voter's tap queue generation.

- `unwrapped.features.enabled` (`UNWRAPPED_ENABLED`, default `true`) is the kill switch. When false:
  both Unwrapped schedulers skip their work, the admin generate and benchmark endpoints and the
  voter Unwrapped endpoints refuse with `409 UNWRAPPED_DISABLED`, and the app hides every
  Unwrapped entry point and shows only the vote results. Queued posts and pending jobs wait
  untouched and resume when the flag is turned back on.
- `unwrapped.features.unwrap-button` (`UNWRAPPED_UNWRAP_BUTTON`, default `true`) is a UI flag.
  When true a vote opens `/posts/{postId}/results`: the vote data with a large Unwrap button at
  the top. When false (and Unwrapped is enabled) a vote opens Unwrapped directly, as before.
- `GET /unwrapped/features` serves both flags to the app.
- Tapping Unwrap calls `POST /posts/{postId}/unwrapped/generate`. The caller must have voted. The
  post is marked for reconciliation only when it has at least
  `unwrapped.reader-generation.minimum-votes` (500) votes. Milestones, human review and
  idempotent job creation are unchanged. Unlike the admin trigger, a voter's tap never retries a
  failed job.

## Reason

- Backend config gives one source of truth and a real kill switch: a flag held only in the app
  cannot stop the schedulers or the admin trigger.
- Serving the flags lets them change without an app release.
- The 500-vote threshold and the existing milestone check bound model spend: repeated taps on the
  same milestone create at most one job, and failed jobs are only retried by an administrator.

## Consequences

- If the flags cannot be loaded, the app treats Unwrapped as off and shows the results page.
- The `unwrapped` Grafana dashboard shows both flags from the `yoursay.unwrapped.feature.enabled`
  gauge, and its Reader API row includes the new routes.
- Changing a flag needs a `post-service` restart.
