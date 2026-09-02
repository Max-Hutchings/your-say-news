# ADR-052 - Author username post attribution

Date: 2026-08-25

## Situation

Every card in the feed attributed its story with the literal text `Author {userId}` - the database
id of the account that published it. That is meaningless to a reader and, on the official Your Say
News account, made the publisher unrecognisable.

Alongside it, posts published from a completed Pepper draft carried an `AI GENERATED` badge, driven
by the server-owned `isAiGenerated` flag on `PostDto`. Official Your Say News stories are drafted
with agent help and then human-reviewed and approved before publication (the `autopost` workflow),
so the badge labelled editorially owned output as machine output.

## Options considered

1. Keep the id label and drop only the AI badge.
2. Fetch each author's public profile from the client (`GET /profiles/{userId}`) per card.
3. Serve the author's public handle with the post itself, decorated in one batched lookup per page,
   and remove the AI badge from the card.

## Decision

Choose option 3.

- `PostDto` gains `authorUsername` - the author's public handle, never their name, email or DOB.
- The posts domain resolves it through its existing `UserServiceClient` boundary, calling a new
  PII-free `YourSayUserService.usernamesByIds(List<Long>)` that returns handles keyed by user id.
  The posts domain never touches the user entity or repository.
- Decoration happens once per page, next to the existing topic-tag decoration, so a page of posts
  costs one user query rather than one per card.
- An author the user domain cannot resolve yields `null`, and the card falls back to
  `Unknown author` rather than failing the read.
- The `AI GENERATED` badge is removed from the post card. `isAiGenerated` stays on the DTO: it is
  still the server-owned provenance record used by the publishing workflows, it is simply no longer
  a reader-facing label.

## Reason

Attribution is a public-profile concern, so the handle is the correct field: it is already the
public identifier shown on profiles and follow lists, and it carries no PII. Serving it with the
post keeps the feed to one request per page - per-card profile fetches would add an N+1 to the
hottest read path in the product.

Removing the badge reflects how official stories are actually made: an agent drafts, a human
reviews and approves, and the company publishes under its own account. Labelling that output as
AI-generated misdescribes who is accountable for it.

## Consequences

- `PostDto` is one field wider; the mobile `Post` type mirrors it as `authorUsername: string | null`.
- Any future consumer of post attribution takes the handle from the post payload; do not add name,
  email or date of birth to this path.
- Tests that mock the posts `UserServiceClient` must stub `usernamesByIds`, otherwise the read path
  has no author labels to decorate with.
