# ADR-024 — Binary and multiple-choice single-select voting

Date: 2026-07-21

## Situation

MVP1 currently treats every support question as a boolean stance. A vote is stored as
`votes.vote_for`, the mobile feed renders Agree and Disagree buttons, and aggregate contracts expose
yes/no counts. That works for questions such as “Should this policy go ahead?”, but it forces less
binary questions into an artificial for/against shape.

MVP1 needs a second voting type in which an official publisher supplies several text answers and a
voter chooses one. The interaction must preserve the current one-vote rule, vote-time characteristic
snapshot, aggregate-only privacy boundary, results gate and Post Unwrapped flow.

The phrase “multiple choice” is potentially ambiguous: this decision requires several available
options but exactly one selected option. Selecting several answers is not part of MVP1.

## Options considered

### 1. Keep every vote binary and encode choices as separate posts

This requires no schema change, but fragments one question across several posts, allows a user to
vote for multiple competing answers and makes the resulting percentages and characteristic
comparisons misleading.

### 2. Keep `vote_for` for binary posts and add a second multiple-choice vote model

This minimises the first migration. It also creates two persistence paths, two DTO families and two
aggregation/analysis implementations for the same rule: one user selects one answer to one post.
Every future privacy, follow-up and milestone change would have to remain consistent across both.

### 3. Store authored options as JSON on the post and put the selected array position on the vote

This is compact, but array positions are weak identifiers. Reordering or editing JSON can silently
change historical meaning, referential integrity cannot prove that a selection remains valid, and
analysis records cannot safely reference a durable option.

### 4. Use one relational option model for both voting types

Every post owns immutable, ordered options with stable IDs. Binary posts receive fixed
Agree/Disagree options; multiple-choice posts receive publisher-controlled options, whether entered
manually or proposed by the post agent. Every canonical
vote references one option.

## Decision

Choose option 4.

MVP1 supports two `VotingType` values:

```java
public enum VotingType {
    BINARY,
    MULTIPLE_CHOICE
}
```

Both are single-select. The existing one-canonical-vote-per-user-per-post constraint remains. A
canonical vote is immutable once submitted.

Each post owns relational option rows with a stable ID, label and ordinal. Binary posts always have
exactly two server-created options with semantic keys `AGREE` and `DISAGREE`; a client cannot rename
or replace them. Multiple-choice posts have 2–5 publisher-controlled options, each 1–120 trimmed
characters and case-insensitively distinct. Multi-select, ranking, write-ins and public-authored
options are deferred.

Voting type, labels and order become immutable when the post is created/published. Votes reference
an option ID, and a composite database foreign key guarantees that the selected option belongs to
the same post. Existing boolean votes are migrated to the corresponding fixed Agree/Disagree option
before `vote_for` is removed.

The create-post page defaults to binary. A **Multiple choice** toggle removes the Agree/Disagree
preview and shows text inputs for authored options, alongside copy stating that voters select one.
The term “multi-select” is not used in the interface. Input order is the default order, and the
publisher can drag and drop options before publication. Accessible move actions provide the same
ordering capability without dragging.

Supporting arguments remain optional for both voting types. They are initially hidden on the create
page; selecting **Add supporting arguments** reveals the existing `caseFor` and `caseAgainst` input
boxes.

In the feed:

- binary posts retain the direct Agree and Disagree buttons;
- multiple-choice posts show one full-width **Have your say...** button;
- pressing it slides an accessible modal sheet up from the bottom to fill most of the screen;
- a voter selects one radio-style row and explicitly confirms before the irreversible canonical
  vote is submitted.

Option-based public DTOs replace boolean and yes/no-specific contracts. This is an intentional
breaking change because the product is not live: existing posts/votes are transformed and no
temporary boolean compatibility or dual-write path is retained. Missing voting type defaults to
binary.

An option with no canonical votes overall is omitted from aggregate responses and result
visualisations, although the post always retains its complete required option definitions. Once an
option has any vote overall, characteristic results include it even in a bucket where its count is
zero. Results remain aggregate-only and gated server-side until the caller has voted. Suppression
rules apply to characteristic buckets exactly as they do for binary voting. Every existing results
visualisation is adapted to N options while following the current design language where possible.

Post Unwrapped stores stable option references and label snapshots in its versioned analysis. Its
follow-up asks the user to choose again from the same option set; the follow-up option remains
separate from canonical results and milestone counts.

The unbiased-post agent always returns a proposed voting type and complete ordered option set.
Binary drafts use fixed Agree/Disagree options. Multiple-choice drafts contain 2–5 generated neutral,
distinct options that the official can edit and reorder before publishing.

## Reason

The product rule is the same in both experiences: a voter selects one of the answers defined by a
post. Modelling that rule once removes boolean-specific branching from persistence, aggregation,
analysis and follow-up voting. Stable relational option IDs preserve historical meaning and let the
database reject a cross-post selection.

Keeping binary semantic keys preserves the deliberate Agree/Disagree editorial treatment without
inferring meaning from display text. The confirmation step in the large sheet reduces accidental
submissions because MVP1 does not allow a canonical vote to be edited.

## Consequences and follow-up work

- Add `post.voting_type`, `post_vote_option` and an option FK on `votes`; backfill all existing posts
  and votes before dropping `votes.vote_for`.
- Extend post create/read DTOs with voting configuration and convert vote DTOs/hooks from booleans
  to option IDs.
- Replace `yesCount`/`noCount` aggregation records with option-aware tallies. Existing endpoints and
  results gating remain, but their response DTO changes.
- Generalise results visualisations and analysis prompts so multiple-choice data is never narrated
  as a binary for/against split.
- Keep case-for/case-against inputs optional for both voting types and reveal them only when the
  publisher selects **Add supporting arguments**.
- Preserve the PII boundary: individual selected options and characteristic snapshots never leave
  the votes domain together, and analysis receives aggregates only.
- Add deterministic 3- and 5-option fixtures alongside migrated binary fixtures. Tests must prove
  exact tallies, option ownership, one-vote locking, result gating and follow-up isolation.
- Metrics may include the bounded voting type and option count, but not option labels, voter
  identity or characteristic values.
- A future ADR is required before adding multi-select, ranked-choice, “Other”/write-in answers or
  editable published options.

The detailed build and rollout sequence is in
[`docs/plans/multiple-choice-single-select-voting.md`](../docs/plans/multiple-choice-single-select-voting.md).
