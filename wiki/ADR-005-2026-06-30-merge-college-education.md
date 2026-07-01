# ADR-005: Merge College Education

Date: 2026-06-30

## Situation

Education is collected as a normalized characteristic bucket. The UI had separate options for
`High school or equivalent` and `Some college / vocational`, but the product should use a simpler
UK-friendly bucket.

## Options Considered

1. Keep separate high-school and some-college buckets.
   This offers slightly more detail, but adds ambiguity across countries.

2. Merge them into one `High school or college` bucket.
   This is simpler and better matches the intended UK-equivalent wording.

## Decision

Remove `Some college / vocational` and label `HIGH_SCHOOL` as `High school or college`.

## Reason

The app needs practical aggregate buckets, not fine-grained education taxonomy. The merged label is
clearer for the intended audience.

## Consequences

- Frontend education options no longer include `SOME_COLLEGE`.
- Backend `EducationLevel` no longer accepts `SOME_COLLEGE`.
- Existing `SOME_COLLEGE` rows migrate to `HIGH_SCHOOL`.
