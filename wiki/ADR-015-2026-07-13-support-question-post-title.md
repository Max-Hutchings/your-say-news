# ADR-015: Use the support question as the post title

## Situation

Posts currently store and display both a headline and a support question. The feed asks readers to vote on the support question, so the separate headline duplicates the post's primary idea and reduces the space available for the article text.

## Options considered

1. Keep both fields and only hide the headline in the feed.
2. Rename the headline column and copy support questions into it.
3. Remove the headline from the data model and use the support question as the sole primary heading.

## Decision

Remove the post `title` field from the database, backend API and mobile types. Creation begins with the support question, and the feed renders that question once in the former headline position.

## Reason

The support question is the action readers respond to. Making it the heading gives every post one clear proposition, removes redundant author input and gives the article body more reading space in the one-post-per-screen feed.

## Consequences and follow-up work

- Existing headline values are intentionally discarded by a Liquibase migration.
- Clients must create and consume posts without a `title` property.
- Any future post-generation agent must treat `supportQuestion` as the primary heading.
- The summary remains independently scrollable and receives the reclaimed feed space.
