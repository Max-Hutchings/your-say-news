# ADR-020 — Controlled canonical topic taxonomy

## Situation

MVP1 Stage 6 needs posts to be discoverable by subject, whether the author identifies the subject or
the system infers it. Arbitrary labels would fragment equivalent subjects, complicate moderation and
give ranking code unstable identifiers. Inferred classifications also need to fail safely and remain
correctable without blocking post publication.

The same catalogue is also needed for a final onboarding “Topics of interest” step. Those selections
are private account-linked personalisation data, whereas the preceding user-characteristic answers
are aggregate-only sentiment dimensions. Combining them would make the privacy promise inaccurate
and place feed-owned data in the wrong service.

## Options considered

1. Allow arbitrary author labels and map common values onto canonical topics.
2. Allow author-selected canonical topics but let inference create new topics.
3. Use one controlled canonical taxonomy for onboarding interests, authors, inference and browsing.

For onboarding interests, we considered adding fields to `CharacteristicAnswers`, storing them in a
new `user-service` table, or storing them in a dedicated `post-service` table owned by `topics`.

For inference, we also considered calling an external model synchronously, calling one
asynchronously, and beginning with a versioned deterministic classifier behind a replaceable
interface.

## Decision

Use one controlled, multi-label taxonomy owned by a new `topics` domain inside `post-service`.
Canonical IDs remain flat for storage and matching, with display groups for a wide onboarding
picker. Authors select up to three canonical IDs, users select zero to seven interests, and inference
may only add canonical IDs. Adding or retiring a topic is a reviewed migration; retirement preserves
historical assignments and user choices.

Store onboarding/settings selections in dedicated `user_topic_interest`, keyed by the authenticated
user ID and canonical topic ID. They never enter `CharacteristicAnswers`, vote snapshots, public
profiles or sentiment breakdowns. The final onboarding step uses explicit private-personalisation
copy instead of the aggregate-only characteristic privacy note. Interests remain optional and do
not change the `user-service` onboarding-complete contract.

Inference runs through an idempotent asynchronous job so publishing never depends on classifier
availability. MVP1 begins with an explainable, versioned deterministic classifier behind an
interface. Assignments preserve source, confidence, classifier version and review state. Admin
actions are audited and take precedence over later worker runs.

Stage 6 makes selected interests available to the feed but does not change MVP1
chronological/follow ordering; topic weighting requires a later decision.

## Reason

A single taxonomy makes onboarding interests, author choices, inferred themes, topic pages and
future feed inputs directly comparable. It prevents spelling/synonym fragmentation and bounds
moderation. Keeping interests in `post-service` gives the feed-owning side one private signal source
without weakening the aggregate-characteristics boundary. Asynchronous, versioned inference
isolates publishing from failures and allows safe reprocessing. Starting with an explainable
classifier gives exact fixtures and inspectable errors before adding an external model's cost and
non-determinism.

## Consequences and follow-up

- `post-service` gains a `topics` domain but no new deployable service.
- The characteristics wizard gains an optional final step backed by `post-service`; existing users
  edit the same interests from Settings and are not forced through onboarding again.
- The server enforces at most seven interests and exposes only the authenticated user's set.
- Interest telemetry is aggregate and uses only bounded canonical topic/operation labels; logs and
  traces never contain identity or an individual's selected-topic set.
- Classifier telemetry measures exact/partial/no overlap with author-selected topics by version and
  canonical topic, while treating author choices as comparison data rather than ground truth.
- Post create accepts IDs only; post reads return canonical topic summaries.
- Existing posts require classification backfill through the normal job path.
- The taxonomy and fixture corpus must evolve together.
- Admin review must preserve original provenance rather than relabelling inference as an author
  choice.
- A future model can replace the classifier implementation without changing persistence or APIs.
- A later ADR is required before topic interests affect ranking weights.
