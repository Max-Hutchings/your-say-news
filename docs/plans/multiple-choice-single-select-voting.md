# Multiple-choice, single-select voting

Detailed implementation plan for the two voting types locked in the
[MVP1 v2 roadmap](mvp1-v2-roadmap.md) and
[ADR-024](../../wiki/ADR-024-2026-07-21-binary-multiple-choice-voting.md).

## Goal

Keep the existing binary Agree/Disagree experience and add a multiple-choice voting type for
questions that have more than two useful answers. Multiple-choice means **one selection from many**
in MVP1; it does not mean that a voter can select several answers.

The complete loop must work for both types: official post creation, feed voting, immutable canonical
vote, aggregate and characteristic results, Post Unwrapped analysis, and the separate follow-up vote.

## Product rules

| Rule | MVP1 behaviour |
| --- | --- |
| Voting types | `BINARY` and `MULTIPLE_CHOICE` |
| Selections per voter | Exactly one canonical option per post |
| Binary options | Fixed server-owned `Agree` and `Disagree` options |
| Multiple-choice options | 2–5 publisher-controlled text options, manually entered or agent-generated, each 1–120 trimmed characters |
| Option uniqueness | Case-insensitively distinct within a post |
| Option order | Input order by default; publisher can drag and drop before publishing |
| Vote editing | Not allowed after canonical submission |
| Option editing | Voting type, labels and order are immutable after post creation/publish |
| Multiple-choice feed control | One full-width `Have your say...` button |
| Choice surface | Modal bottom sheet, approximately 86% screen height, single-select plus confirmation |
| Result access | Existing must-have-voted server-side gate applies to both types |
| Supporting arguments | Optional for both types; fields appear after selecting `Add supporting arguments` |
| Out of scope | Multi-select, ranking, “Other”, write-ins, public option creation and editable published options |

Use the create-page label **Multiple choice**, followed by “Voters can select one option.” Do not
label the toggle “multi-select”, because that describes a different voting rule.

## Current baseline and affected areas

The current implementation stores `votes.vote_for` as a boolean, exposes `voteFor` in vote DTOs,
and shapes every aggregate as `yesCount`/`noCount`. The mobile `VoteControls`, sentiment views and
create-post preview also assume Agree/Disagree. This feature therefore changes a shared contract;
it is not only a new bottom sheet.

No `user-service` schema or endpoint changes are required. Characteristic collection and the
vote-time `CharacteristicSnapshot` stay unchanged.

Affected `post-service` domains:

- `posts`: owns a post's voting type and immutable ordered option definitions.
- `votes`: owns selected-option votes, uniqueness, snapshots and option-aware aggregation.
- `analysis`: consumes generic option aggregates and records option-based follow-up responses.
- `agent`: always proposes a voting type and complete ordered option set as part of its draft.

Affected mobile features:

- `features/posts`: create controls, API types and post-card integration.
- `features/votes`: option-based vote state, multiple-choice sheet and generic result rendering.
- Post Unwrapped routes/components when Stage 5 is implemented.

## Data model and migration

Add `post-service/src/main/resources/db/migrations/0010-add-voting-options.xml`. Keep schema migration
and seed changes separate, following the existing changelog convention.

### Post voting type

Add `post.voting_type VARCHAR(32) NOT NULL DEFAULT 'BINARY'` with a check constraint restricted to
`BINARY` and `MULTIPLE_CHOICE`.

The default makes every existing post binary during migration and makes binary the permanent default
when a create request does not explicitly select another type. New backend DTOs should still send it
explicitly.

### Vote options

Create `post_vote_option`:

| Column | Rule |
| --- | --- |
| `id BIGINT` | Generated primary key; this is the value stored by a vote |
| `post_id BIGINT` | Required FK to `post.id` |
| `label VARCHAR(120)` | Required, stored trimmed |
| `ordinal SMALLINT` | Required, zero-based display order |
| `semantic_key VARCHAR(32)` | `AGREE`/`DISAGREE` for binary options; null for authored options |
| `created_at TIMESTAMPTZ` | Required audit timestamp |

Add unique constraints/indexes for `(post_id, ordinal)`, `(post_id, id)` and the applicable binary
`(post_id, semantic_key)` value. Enforce case-insensitive option-label uniqueness in application
validation and with a unique `lower(label)` index per post where PostgreSQL/Liquibase support is
practical.

Every post has option rows:

- `BINARY`: ordinal 0 / `AGREE` / “Agree”; ordinal 1 / `DISAGREE` / “Disagree”.
- `MULTIPLE_CHOICE`: 2–5 rows with null `semantic_key` and publisher-controlled labels.

`Post` remains the aggregate root. Persist its option rows in the same transaction as post creation;
there must never be a visible post without a complete voting configuration.

### Canonical votes

Replace the boolean stance with an option reference:

1. Add nullable `votes.option_id BIGINT`.
2. Insert the two fixed binary options for every existing post.
3. Backfill `votes.option_id` by mapping `vote_for=true` to that post's `AGREE` option and `false` to
   its `DISAGREE` option.
4. Verify no vote remains without an option.
5. Add a composite FK from `votes(post_id, option_id)` to
   `post_vote_option(post_id, id)`. This guarantees at database level that an option belongs to the
   same post as the vote.
6. Make `option_id` non-null and drop `vote_for`.

Retain `uk_votes_post_user`; it remains the authoritative one-canonical-vote rule. Index
`votes(post_id, option_id)` for aggregation.

The product is not live, so this is an intentional coordinated breaking migration. Transform all
existing posts and votes, verify the backfill, drop `vote_for`, and update backend and mobile
contracts together. Do not add dual-write or temporary `voteFor` compatibility code.

### Seed and fixture data

- Existing seeded posts become binary and receive fixed option rows.
- Existing seeded votes are migrated, not recreated.
- Add at least two multiple-choice posts: one with 3 options and one with 5, including deterministic
  vote distributions across characteristic buckets.
- The reset seed changelog must delete votes before options and options before posts to satisfy FKs.

## Backend domain model

In `posts`, add top-level public types `VotingType`, `VoteOptionDto` and a PII-free voting-config
contract. Put `PostVoteOption` and its repository under `posts/model`.

`VoteOptionDto` contains `id`, `label`, `ordinal` and nullable `semanticKey`. The semantic key lets
binary UI/results retain their established editorial meaning without inferring it from editable text.

Expose voting configuration to the sibling `votes` domain through a public top-level interface/DTO,
not by importing `posts.model`. The vote service validates the post and selected option through that
contract inside the cast flow; the composite database FK remains defence in depth against races or
bad internal callers.

Validation on post creation:

- Missing `votingType` is always treated as `BINARY`; binary is the product default, not only a
  migration fallback.
- `BINARY` rejects client-authored options and creates the fixed pair server-side.
- `MULTIPLE_CHOICE` requires 2–5 labels, trims them, rejects blanks, labels over 120 characters and
  case-insensitive duplicates, and persists request order.
- `caseFor` and `caseAgainst` remain optional for either voting type. The backend accepts and returns
  them unchanged; the create UI reveals both only after the publisher selects **Add supporting
  arguments**.
- Publisher authorisation from ADR-023 remains mandatory and unchanged.

Future post-update and draft-publish endpoints must reject changes to `votingType` or options after
publication. The current create-immediately-publishes flow makes the configuration immutable from
the moment the transaction commits.

## API contracts

### Create and read posts

Extend `CreatePostRequest`:

```jsonc
// Binary; voteOptions omitted or empty
{
  "summary": "Context",
  "supportQuestion": "Should the policy go ahead?",
  "votingType": "BINARY",
  "voteOptions": [],
  "media": []
}

// Multiple choice; request order becomes display order
{
  "summary": "Context",
  "supportQuestion": "Which change should happen first?",
  "votingType": "MULTIPLE_CHOICE",
  "voteOptions": [
    { "label": "More frequent buses" },
    { "label": "Protected cycle lanes" },
    { "label": "Lower town-centre parking charges" }
  ],
  "media": []
}
```

Extend every `PostDto` with `votingType` and ordered `voteOptions`. Binary responses include the two
server-created options; clients must use their IDs rather than fabricate option IDs locally.

Retain optional `caseFor` and `caseAgainst` fields in create/read contracts for both voting types.
Extend mobile `CreatePostFields` and `CreatePostInput` to send them when **Add supporting arguments**
is selected and otherwise send null/omit them.

Extend `AgentDraftDto` with `votingType` and ordered `voteOptions`. The agent response validator must
require a complete valid set on every draft; the binary create adapter omits/normalises the returned
fixed pair before the server creates its authoritative binary option rows.

### Cast and retrieve a canonical vote

Replace `voteFor` with `optionId`:

```jsonc
// POST /votes
{ "postId": 42, "optionId": 103 }

// 201 / GET /votes/42/mine
{ "id": 9001, "postId": 42, "optionId": 103 }
```

The identity still comes only from the JWT. Preserve 409 for a duplicate canonical vote. Add precise
400 errors for a missing option and an option that is not available on the named post; return 404
for an unknown post. Never log the caller together with a selected label or characteristic snapshot.

### Aggregate results

Keep the existing gated endpoints:

- `GET /votes/{postId}/sentiment`
- `GET /votes/{postId}/sentiment/{axis}`

Replace binary-only buckets with an option-aware response. A representative shape is:

```jsonc
{
  "postId": 42,
  "votingType": "MULTIPLE_CHOICE",
  "characteristic": "OVERALL",
  "options": [
    { "id": 103, "label": "More frequent buses", "ordinal": 0, "semanticKey": null },
    { "id": 104, "label": "Protected cycle lanes", "ordinal": 1, "semanticKey": null }
  ],
  "buckets": [
    {
      "bucket": "OVERALL",
      "total": 50,
      "choices": [
        { "optionId": 103, "count": 30, "percentage": 60.0 },
        { "optionId": 104, "count": 20, "percentage": 40.0 }
      ]
    }
  ],
  "suppressedBuckets": 0
}
```

Omit an option from the aggregate response and visualisations while its overall canonical count is
zero. The complete required option set remains available on `PostDto`. Once an option has at least
one vote overall, include it in stable ordinal order in every surfaced bucket, including an explicit
zero in a particular characteristic bucket when applicable. Bucket counts sum to `total`; unrounded
percentages sum mathematically to 100. Overall is never suppressed under the current contract.
Characteristic buckets continue to use the configured `suppressBelow` threshold.

Update `SentimentTally` to count by option ID rather than branching on a boolean. Its pure input is
an identity-free `VoteSnapshot(optionId, characteristicSnapshot)`. It may receive the post's public
option definitions, but never the voter ID.

## Mobile post creation

Update `CreatePostScreen`, `use-create-post`, post types and `PostService` together.

- Default the form to binary so the current fast path is unchanged.
- Add a `Multiple choice` switch/toggle near the support question.
- Binary mode shows the current Agree/Disagree preview and binary guidance.
- Multiple-choice mode removes that preview and displays two initial option inputs, an “Add option”
  control until 5, and remove controls while more than 2 remain.
- Preserve typed custom options in local state if the publisher toggles away and back, but do not
  submit them while binary is selected.
- Use a drag handle on every multiple-choice option. Input order is the initial order; drag and drop
  updates the visible array and submitted ordinals. Provide accessible move-up/move-down actions as
  an equivalent for screen-reader and keyboard users.
- Display per-option validation beside the relevant input and a form-level duplicate warning.
- State explicitly: “Voters can select one option.”
- Show an unchecked **Add supporting arguments** control for both voting types. When selected, reveal
  the optional `caseFor` and `caseAgainst` inputs; clearing the control hides and clears them only
  after confirmation if either contains text.
- The publish action sends the voting type and trimmed labels. It remains blocked while any required
  option is invalid or media/post submission is in flight.

Option ordering always follows the visible drag-and-drop order at the moment of publication.

Update the post agent contract so every generated draft contains `votingType` and `voteOptions`.
Binary drafts return the fixed Agree/Disagree set, which the server normalises to system-owned
options. Multiple-choice drafts return 2–5 concise, neutral, mutually distinct choices in a proposed
order. The official can edit and drag generated multiple-choice options before publishing. Agent
validation rejects missing, overlapping, duplicated, leading or otherwise invalid option sets and
retries or returns the draft for manual correction.

## Mobile voting interaction

Change the post card to pass the post's voting configuration into the votes feature.

### Binary

Keep the current Agree/Disagree controls, styling, loading state and accessibility semantics. Each
button submits its server-provided option ID. Existing binary behaviour should appear unchanged.

### Multiple choice

Render one full-width **Have your say...** button in the space occupied by the two binary buttons.
Pressing it opens a new `MultipleChoiceVoteSheet`:

- modal, bottom-aligned, slide animation, backdrop and grab handle;
- approximately 86% screen height, using safe-area insets and a scrollable option list;
- repeats the support question so the choices retain context;
- each option is a radio-style row with its full label and a large touch target;
- one row can be selected locally; tapping the backdrop/back button before submission casts nothing;
- a fixed “Submit choice” button is disabled until a row is selected;
- while submitting, prevent repeated requests and keep failures safely retryable without losing the
  selected row;
- display auth, network and server errors without losing the local selection;
- use radio roles/selected state, a labelled radio group where supported, focus the sheet heading on
  open, and support reduced motion.

After success, lock the card, close the selection sheet and open Post Unwrapped (or the current
results/story-building sheet until Stage 5 lands). The collapsed control can read
“You chose — {label}”. “See how others voted” remains available after either voting type.

Refactor `useVote` from `boolean | null` to `optionId | null`. Duplicate reconciliation loads the
stored option ID. It must never fall back to an attempted option after a 409 if the stored vote
cannot be retrieved, because showing the wrong chosen label would be misleading; lock into a neutral
“Vote already recorded” state and retry the lookup.

## Results and Post Unwrapped

Retain the strong teal/coral treatment for binary semantic options. Do not assign Agree/Disagree
meaning to arbitrary multiple-choice options.

For multiple choice:

- introduce an option-aware horizontal bar/list with labels, exact counts and percentages;
- use stable theme tokens and always show labels/values, so colour is never the only differentiator;
- adapt every existing Counts, Bars, Table and Columns mode to N options while following the binary
  design language where possible; no visualisation mode is hidden solely because a post is multiple
  choice;
- keep characteristic selection, gating, suppression disclosure, loading and failure states.

The analysis input/output schema identifies choices by stable option ID and stores the label snapshot
used for that analysis version. Insight ranking compares each option's cohort share with its overall
share; it must not force a many-option result into “for” and “against”.

Post Unwrapped's initial choice slide shows the selected label. Its overall and cohort slides support
N choices. On the final follow-up slide, show the same option set and store both original and follow-up
option IDs. Equality means unchanged; inequality means changed. The follow-up remains physically and
logically excluded from canonical aggregates and milestone triggers.

## Error handling and observability

Backend validation/error cases:

- unknown post;
- missing option ID;
- option belongs to another post;
- malformed voting type;
- wrong option count, blank/long/duplicate option labels;
- client-supplied options on binary create;
- duplicate canonical vote;
- publisher not authorised.

Metrics should distinguish `voting_type` and success/failure for post creation, sheet open, vote cast,
results load and analysis generation. Keep metric dimensions bounded: record the enum and option
count, never option labels, voter identifiers or characteristic values. Logs must likewise omit the
selected label and snapshot.

## Test plan

Write tests for behaviour and exact values, then run the repository's `test-audit` skill because the
suites are being changed.

### Backend unit tests

- Post voting-config validator: binary defaults/fixed options; multiple-choice min/max, trimming,
  blank, length and case-insensitive duplicates.
- Agent draft validator: every draft has a valid voting type and complete option set; generated
  multiple-choice options are 2–5, distinct and non-overlapping; invalid output retries or fails
  explicitly.
- Generic tally: 2-, 3- and 5-option distributions; globally zero-count options omitted; an option
  with votes overall retained as zero inside a subgroup; exact counts and percentages;
  characteristic buckets; suppression; deterministic option order; empty result.
- Vote service: accepts an option on the post, rejects cross-post/unknown options, preserves the
  characteristic snapshot and maps the response without identity.
- Analysis insight selection: multiple-option differences, sparse data and no forced binary wording.

### Backend integration tests

- Migration backfills existing true/false votes to Agree/Disagree option IDs exactly.
- Create/read round-trip for binary and multiple-choice posts, including persisted order.
- Transaction rollback leaves no post or orphan options after a validation/persistence failure.
- Cast/get-mine round-trip for both types; duplicate is 409; missing/foreign option is rejected.
- Composite FK prevents inserting a vote with an option from another post.
- Overall and characteristic APIs return exact per-option values and remain gated until voting.
- Existing seeded binary post results are unchanged in meaning after migration.
- Follow-up selection never changes canonical results or analysis milestone counts.

### Frontend unit/integration tests

- Create form defaults to binary and submits no authored options.
- Toggle replaces Agree/Disagree preview with option inputs; add/remove limits and validation work;
  typed values survive a temporary toggle away and back.
- **Add supporting arguments** reveals optional fields for either type and submitted values survive
  the create/read round trip; cancelling with entered text requires confirmation.
- Binary post card still renders and submits Agree/Disagree using option IDs.
- Multiple-choice card has only `Have your say...`; the sheet mounts, dismisses without a vote,
  requires one selection and submits the exact selected ID once.
- Loading, duplicate reconciliation, auth, network, retry and previously-voted states are truthful.
- 3- and 5-option sheets remain scrollable and accessible at small screen sizes and large font scale.
- Dragging changes submitted order; default input order is retained without dragging; accessible
  move actions produce the same order.
- Counts, Bars, Table and Columns render exact N-option labels/counts/percentages and never expose
  binary-only labels for arbitrary options.
- Agent review renders every generated option and permits multiple-choice editing and reordering
  before publication.
- Post Unwrapped and follow-up preserve selected IDs and canonical totals.

## Delivery order

1. Add ADR and lock DTO shapes.
2. Add the Liquibase option schema, binary backfill and seed updates.
3. Implement post voting configuration and create/read contracts.
4. Convert canonical vote storage/service/API from boolean to option ID.
5. Generalise aggregation and gated result DTOs.
6. Update mobile post types and binary controls to use server option IDs; verify no visual regression.
7. Add create-page multiple-choice inputs and the voting bottom sheet.
8. Add multiple-choice results, post-agent option generation, analysis and follow-up support.
9. Run targeted backend/frontend suites, the test audit, full regression and a manual accessibility
   pass on iOS and Android.

Do not ship a state in which officials can publish multiple-choice posts before the feed can render
and vote on them. Gate creation behind a temporary capability flag if backend and mobile cannot be
deployed together.

## Acceptance criteria

- An authorised official can create a binary post without entering options and a 2–5 option
  multiple-choice post with ordered labels.
- Publishers can drag multiple-choice options into their preferred order and optionally reveal and
  complete supporting-argument fields for either voting type.
- Invalid or duplicate option labels cannot be published, and ordinary users still cannot publish.
- Binary posts look and behave as they do today.
- A multiple-choice post shows one `Have your say...` button; its large bottom sheet allows exactly one
  selection and casts only after confirmation.
- The server accepts only an option belonging to the post and only one canonical vote per user/post.
- Both voting types return exact option-aware overall and characteristic aggregates without identity.
- Result visualisations support every existing view mode and omit options with no canonical votes
  overall while retaining the complete required definitions on the post.
- Every post-agent draft contains a valid proposed voting type and complete option set.
- Post Unwrapped and its separate follow-up choice work with either type without changing the
  canonical vote.
- Existing posts and votes retain their Agree/Disagree meaning after migration.
