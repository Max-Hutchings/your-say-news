# ADR-034 — Synthetic development database location

**Date:** 2026-07-29  
**Status:** Accepted

## Situation

The development proof of concept uses Aiven PostgreSQL Free. Aiven chooses and may change the Free
service's cloud, region or configuration. Earlier planning made written confirmation of an exact
European database/backup boundary a provisioning gate.

The environment remains synthetic-data-only until the privacy and operational Gate D is complete.
Cost is more important than database latency or exact placement during this stage.

## Options considered

1. Block provisioning until Aiven guarantees a European location.
2. Pay for a region-selectable Aiven or Scaleway database now.
3. Accept Aiven's provider-assigned Free location for synthetic proof-of-concept data.

## Decision

Use Aiven PostgreSQL Free in its provider-assigned location for the synthetic-data proof of
concept. Exact database residency is not a Gate B requirement.

Retain a fixed-region paid provider as a fallback if Aiven Free becomes unavailable, exceeds its
limits or an exact EU location becomes mandatory.

## Reason

No real user data is allowed during this stage, and a fixed-region paid database would consume
most of the approved £20 monthly infrastructure budget without improving proof-of-concept
functionality.

## Consequences

- Terraform accepts the exact Aiven cloud and plan identifiers available to the account.
- The application must continue using synthetic data until Gate D.
- Before admitting real testers, review processor terms and choose a compliant region-selectable
  database if the final residency requirement demands one.
- Immutable EU jurisdiction remains required for both R2 buckets.
