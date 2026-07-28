# ADR-037 — Public domain DTO packages

## Situation

DTO declarations at the top level of each backend domain obscure the controllers and service
contracts, making the domain's chain of events harder to scan. The existing package rule otherwise
treats every sub-package as internal.

## Options considered

1. Keep DTOs at the domain package top level.
2. Put DTOs in an internal package and expose them indirectly.
3. Make `dto` the only public sub-package of every domain.

## Decision

Use `<domain>.dto` for DTOs. A domain may expose its top-level controllers and public interfaces,
plus types in its `dto` package. Every other sub-package remains internal to that domain.

## Reason

This keeps the domain's entry points and event flow immediately readable while preserving explicit,
extractable boundaries for cross-domain data contracts.

## Consequences

- Cross-domain callers import DTOs from `<domain>.dto`.
- New DTOs must be created in `dto`, not at the domain package top level.
- No other sub-package becomes part of a domain's public API.
