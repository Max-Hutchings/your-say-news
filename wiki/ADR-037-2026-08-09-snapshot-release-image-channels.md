# ADR-037 — Separate snapshot and release image channels

## Situation

The development deployment publishes an application image and a Liquibase migration image for
every relevant commit. Future production deployments need a distinct, auditable channel containing
only versioned releases. Reusing one GHCR package set for both would blur commit snapshots and
approved production releases.

## Options considered

1. Publish commit and version tags into the same two GHCR packages.
2. Use separate snapshot and release package sets.
3. Run a container registry on the application VM.

## Decision

Use separate GHCR package sets:

- development snapshots:
  `ghcr.io/max-hutchings/your-say-news-post-service-snapshot` and
  `ghcr.io/max-hutchings/your-say-news-migrations-snapshot`;
- future production releases:
  `ghcr.io/max-hutchings/your-say-news-post-service` and
  `ghcr.io/max-hutchings/your-say-news-migrations`.

Snapshot images are built from a commit and tagged `sha-<seven-character commit SHA>` so operators
can identify them quickly. Image labels and sealed snapshot metadata retain the full 40-character
SHA; deployments remain pinned to the immutable digest. Future release images are **promoted, not
rebuilt**: a production workflow triggered by an approved version tag copies the exact snapshot
digest that passed on dev into the release packages (`docker buildx imagetools create` or
`crane copy`), adds the version tag, and deploys by that same digest. This ADR reserves the release
package names but does not create a production workflow or publish production images.

The image holds no environment-specific values. It runs as dev or prod based only on what is
injected at container start: `QUARKUS_PROFILE` and that environment's secrets (see
`docs/plans/backend-environment-config.md`).

The development workflow exposes separate jobs for test, post-service image build, migration image
build, publication/sealing and explicit manual deployment. Image build jobs never push; the publish
job is the only job allowed to write the two snapshot packages.

## Reason

Separate package names make it immediately clear whether an artifact is an unpromoted commit
snapshot or an approved versioned release. Independent workflow jobs make failures and provenance
visible without weakening the rule that only tested artifacts are published and only explicitly
selected snapshots are deployed.

Promoting the digest instead of rebuilding proves that prod runs byte-for-byte what was tested on
dev. A rebuild from the same commit can still differ (base image updates, dependency resolution,
build timestamps), which would make dev testing evidence for a different artifact.

*Amended 2026-10-02: release images changed from "built by the production workflow" to "promoted
snapshot digests", so dev and prod run the same image.*

## Consequences and follow-up work

- The first successful development push creates the two private snapshot packages automatically.
- Image tags, image-archive artifacts and sealed deployment snapshots include the same short SHA.
- Confirm both packages inherit access from this repository and configure snapshot retention.
- Create the two reserved release packages only when the future version-tagged production workflow
  first promotes a snapshot into them.
- The production workflow must only accept a snapshot digest that has a successful dev deployment
  and health check, so nothing reaches prod without running on dev first.
- Keep the VM rollout directories called releases: they are atomic deploy/rollback units and are
  independent of the GHCR snapshot-versus-release channel.
- Never publish or deploy `latest`.
