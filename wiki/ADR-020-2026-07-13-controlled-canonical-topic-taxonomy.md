# ADR-020 - Governed topic tag taxonomy

## Situation

MVP1 Stage 6 needs content to be discoverable by subject, whether a creator selects the subject or
the system infers it. The subject is not part of the post itself. It is a reusable label that is
applied to a post and may later be applied to other product entities.

Free-form hashtags would fragment equivalent subjects, complicate moderation and give feed queries
unstable identifiers. The UI also needs clean topic chips rather than user-authored `#hashtags`.

The same catalogue is needed for a private "Topics of interest" picker. Those selections are
account-linked personalisation data, whereas user-characteristic answers are aggregate-only
sentiment dimensions. Combining them would weaken the privacy boundary and put feed-owned data in
the wrong domain.

## Options considered

1. Store free-form hashtags on each post and map common values later.
2. Store post-specific topics that cannot be reused by other product areas.
3. Use one governed topic tag catalogue for creator selection, classification, interests and
   browsing.

For tag relationships, we considered storing arrays on a post, using a generic
`entity_type/entity_id` table, and using explicit relationship tables for each supported entity.

For inference, we considered calling an external model synchronously, calling one asynchronously,
and beginning with a versioned deterministic classifier behind a replaceable interface.

## Decision

Use one controlled, multi-label `topic_tag` catalogue owned by the `topics` domain inside
`post-service`. Topic tags have stable IDs, product-controlled display labels and display groups.
They render as clean chips without a `#` prefix.

Creators select up to three active topic tag IDs. Users select zero to seven topic tag interests.
Inference and admin corrections may only assign IDs from the same catalogue. There is no public
path for creating arbitrary tags during post creation.

Use explicit relationship tables rather than arrays or a polymorphic association:

- `post_topic_tag_assignment` records each creator, classifier or admin assignment with its
  provenance;
- `effective_post_topic_tag` contains the current searchable `(post_id, topic_tag_id)` projection
  used for post chips and category feed queries; and
- `user_topic_tag_interest` stores private account-linked interests.

If topic tags are later applied to another entity, add a relationship for that entity, such as
`collection_topic_tag`. Explicit tables preserve real foreign keys and keep ownership clear.

Assignments preserve source, confidence, classifier version and review state. Creator assignments
use source `CREATOR`; confidence and classifier version apply only to inferred assignments. Admin
actions are audited, preserve the original assignment history and take precedence over later worker
runs. The effective projection is rebuilt when assignment state changes.

Inference runs through an idempotent asynchronous job so publishing never depends on classifier
availability. MVP1 begins with an explainable, versioned deterministic classifier behind an
interface. A later classifier can replace it without changing persistence or APIs.

Store onboarding and settings selections in `user_topic_tag_interest`, keyed by authenticated user
ID and topic tag ID. Interests never enter `CharacteristicAnswers`, vote snapshots, public profiles
or sentiment breakdowns. Interests remain optional and do not change the `user-service`
onboarding-complete contract.

ADR-043 later amends catalogue management: MVP1 starts with a smaller governed set and authorised
admins may add or retire topic tags at runtime. Retirement preserves historical relationships.

## Reason

One reusable catalogue makes creator choices, inferred subjects, user interests and category feeds
directly comparable through the same stable ID. It prevents spelling and synonym fragmentation,
keeps display copy controlled and makes feed filtering an indexed relationship query.

Separating assignment history from the effective projection preserves classifier and admin audit
data without making every public read recalculate precedence rules. Explicit relationship tables
also allow topic tags to be reused without losing database foreign-key protection.

Keeping interests in `post-service` gives the feed-owning side one private signal source without
weakening the aggregate-characteristics boundary. Asynchronous inference isolates publishing from
failures and permits safe reprocessing.

## Consequences and follow-up

- `post-service` gains a `topics` domain but no new deployable service.
- Post creation accepts topic tag IDs only and enforces at most three creator selections.
- Post reads return effective topic tag summaries, not assignment provenance.
- Category feeds query `effective_post_topic_tag` by topic tag ID.
- The server enforces at most seven private interests and exposes only the authenticated user's set.
- Existing posts require classification or controlled backfill through the normal assignment path.
- Admin review changes effective state without rewriting original creator or classifier provenance.
- Logs and traces never contain identity, post text or an individual's topic tag interest set.
- Metrics use bounded tag, source, version, operation and outcome labels.
- Topic tag interests do not affect cross-category ranking until a later decision defines that use.
