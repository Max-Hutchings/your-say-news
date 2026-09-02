# Required output contract

- Return exactly ten non-duplicate stories in one `stories` list.
- Do not return an operational status, failure reason, search failure, placeholder or
  inability-to-verify explanation. The application, not the model, decides whether research failed.
- Include at least one UK, one US and one GLOBAL story.
- Rank the stories from 1 to 10 with no repeated rank.
- For every story return a non-blank `headline`, `summary` and `deduplicationKey`.
- For every story return `publishedAt` as an ISO-8601 UTC string, such as
  `2026-08-23T12:34:56Z`, and return one primary `region`.
- For every story return at least one source with a non-blank exact `url`, `title` and `publisher`.
- The `deduplicationKey` must identify the underlying event, so duplicate publisher coverage shares
  the same key and is removed before returning the list.
- You must call web search before returning the stories.
