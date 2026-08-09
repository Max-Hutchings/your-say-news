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
SHA; deployments remain pinned to the immutable digest. Future release images will be built only by a production workflow triggered by
an approved version tag and will also be deployed by digest. This ADR reserves the release package
names but does not create a production workflow or publish production images.

The development workflow exposes separate jobs for test, post-service image build, migration image
build, publication/sealing and explicit manual deployment. Image build jobs never push; the publish
job is the only job allowed to write the two snapshot packages.

## Reason

Separate package names make it immediately clear whether an artifact is an unpromoted commit
snapshot or an approved versioned release. Independent workflow jobs make failures and provenance
visible without weakening the rule that only tested artifacts are published and only explicitly
selected snapshots are deployed.

## Consequences and follow-up work

- The first successful development push creates the two private snapshot packages automatically.
- Image tags, image-archive artifacts and sealed deployment snapshots include the same short SHA.
- Confirm both packages inherit access from this repository and configure snapshot retention.
- Create the two reserved release packages only when the future version-tagged production workflow
  first publishes them.
- Keep the VM rollout directories called releases: they are atomic deploy/rollback units and are
  independent of the GHCR snapshot-versus-release channel.
- Never publish or deploy `latest`.
