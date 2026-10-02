# ADR-056 - Main auto-deploys to development

## Status

Accepted on 2026-10-02. Replaces the manual development deployment described in
`docs/development-deployment-handover-2026-09-06.md`.

## Situation

`dev-app.yml` built a deployable snapshot on a push to any branch, and an operator deployed it by
dispatching the workflow with a run ID, full SHA and the phrase `deploy development`. Any branch
could therefore reach the development server, and `main` was never deployed automatically. The
snapshot build also did not wait for the frontend or admin frontend tests in `ci.yml`.

## Options considered

1. Keep manual dispatch, restricted to `main`.
2. Trigger `dev-app.yml` with `workflow_run` when CI completes on `main`.
3. Call `dev-app.yml` as a reusable workflow from a CI job that needs every test job.

## Decision

Option 3. `ci.yml` gains a `deploy development` job that runs only on a push to `main`, needs the
backend, frontend and admin frontend jobs, and calls `dev-app.yml` (now `workflow_call` only). The
manual dispatch path and its `authorize-deploy` action are removed.

## Reason

- One run tests, builds and deploys one commit. `workflow_run` executes against the latest `main`
  rather than the tested commit, so stacked pushes could deploy code CI never passed.
- `main` becomes the only route to the development server, and it is always current.
- `needs` makes "all tests pass" a hard precondition with no cross-workflow polling.

## Consequences

- Every push to `main` deploys, including frontend-only changes; redeploying an unchanged backend
  is harmless.
- CI no longer cancels in-progress `main` runs, and the deploy job queues on the
  `application-development` concurrency group, so a release is never interrupted.
- Feature branches no longer build snapshot images; their backend tests run in CI's pull request
  checks.
- Retry by re-running failed jobs in the CI run; roll back by reverting on `main`.
