# ADR-025 — User domains inside post-service

## Situation

The MVP currently deploys user and post capabilities separately even though they share one
Postgres database. Post, feed, vote and post-agent code crosses that boundary through several
HTTP REST-client interfaces. This adds local-development and runtime coordination for domains that
are intended to remain independently structured but no longer need a network boundary.

The standalone `user-service` must remain in the repository during the transition so the combined
service can be proven before the old deployable is removed.

## Options considered

1. Keep both deployable services and their REST calls unchanged.
2. Move and reshape all user code directly into the existing post domains.
3. Copy the user, user-characteristic and social domains under `post-service`, keep their internal
   package structure, and replace the existing REST clients with local adapters of the same names.

## Decision

Use option 3.

`post-service` now contains the copied user domains under `com.yoursay.user`. Existing post-side
client names and method contracts remain as compatibility adapters, but they inject and call the
copied public domain services instead of making HTTP requests. The unused bearer arguments remain
temporarily so callers do not need a wider signature change in this step.

The post Liquibase master and seed changelogs include copies of the user migrations and fixtures.
Those copies retain the original Liquibase logical file paths, so databases already migrated by
`user-service` recognise the same changeSets rather than attempting to recreate tables.

## Reason

This removes the runtime network dependency while preserving the DDD boundaries and most call
sites. Keeping the adapter names makes the change small and leaves a clear seam if a domain is
extracted again later. Preserving Liquibase identities supports both a fresh post-service database
and the existing shared database during the transition.

## Consequences and follow-up

- `post-service` requires both JDBC Hibernate ORM for the copied user domains and Hibernate
  Reactive for its existing post domains; both continue to use the shared datasource.
- User migrations and seed files must remain synchronised in both modules until `user-service` is
  removed.
- The standalone `user-service` module, configuration and source remain unchanged for now.
- Once the combined service has been proven in development and deployment, remove the old service,
  its Compose/Liquibase pass and the now-unused compatibility parameters and REST-client
  dependencies in a separate change.
