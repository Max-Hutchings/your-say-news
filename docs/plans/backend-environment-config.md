# Backend environment config

**Status:** implemented (ADR-055). Outstanding: full Testcontainers suite and the dev deployment
check (step 11) - both need Docker / a deploy run.

Make `post-service` config say **local**, **dev** and **prod** and mean exactly that (see
"Environments" in `AGENTS.md`), with one clear rule for where each kind of value lives.

## Current situation

- Quarkus `%dev` = a laptop. The hosted dev server runs `QUARKUS_PROFILE=prod`
  (`service/deploy/compose.yaml`). Real prod does not exist.
- Base (un-prefixed) `application.properties` holds laptop values: LocalStack S3 with `test/test`
  keys, CORS for `localhost`, Liquibase migrate-at-start, Quinoa serving the admin UI. The hosted
  server only works because compose overrides each one.
- Hosted dev config is split across three places: `application.properties`,
  ~25 `QUARKUS_*` mappings in `service/deploy/compose.yaml`, and `render-runtime-env.sh`.
  Reading one file does not tell you what dev runs with.
- Telemetry labels use `quarkus.profile` (`DomainMetrics`, `AiTokenMetrics`,
  `UnwrappedFeatureFlags`). **Grafana shows the hosted dev server as `environment="prod"`** and
  laptops as `environment="dev"`.
- 7 Firebase/admin-session classes use `@IfBuildProfile("dev")`, meaning "laptop only".

## Decisions

### 1. Quarkus profile names match our environment names

| Profile | Where | Set by |
|---|---|---|
| `local` | `quarkusDev` on a laptop, smoke tests | `-Dquarkus.profile=local` on the `quarkusDev` task in `post-service/build.gradle.kts` |
| `test` | `@QuarkusTest` | Quarkus default, unchanged |
| `dev` | hosted dev VM | `QUARKUS_PROFILE=dev` in `service/deploy/compose.yaml` |
| `prod` | future | future prod deploy |

`%dev.` stops meaning "Quarkus dev mode". Quarkus has two separate switches:

- **Launch mode** - set by *how* it starts. `quarkusDev` = DEVELOPMENT (hot reload, Dev UI, Dev
  Services). `java -jar` in the image = NORMAL, always. Dev-mode tooling is not even packaged.
- **Profile** - only chooses which `%name.` lines in `application.properties` apply.

So the hosted VM runs a normal production image (launch mode NORMAL) that reads `%dev.` lines.
Checked against Quarkus 3.36.2: none of our 124 Quarkus/Quarkiverse runtime jars ship `%dev.` or
`%prod.` defaults, and launch-mode defaults such as `quarkus.http.host` (`localhost` vs `0.0.0.0`)
are keyed on `LaunchMode`, not the profile name.

Rejected: keep Quarkus defaults and document a translation table. Every agent and engineer reading
`%dev.` would keep guessing wrong - which is how we got here.

### 2. Base config is hosted-safe; `local` opts in to laptop conveniences

Un-prefixed values must be correct (or fail closed) on a server. Anything that only makes sense on a
laptop moves under `%local.`:

- LocalStack S3 endpoint, path-style and `test/test` keys
- CORS enabled + `localhost` origins (base: CORS off)
- Compose Postgres URL/user/password
- Firebase emulator settings, admin cookie settings, Dev UI permit
- OTLP to `localhost:4317`, full failed agent response logging
- Quinoa serving the admin UI (base: `quarkus.quinoa.just-build=true`)
- `quarkus.liquibase.migrate-at-start=true` (base: `false` - hosted runs the migration container)

Why: a missing override on a server today silently falls back to laptop values (e.g. fake S3 keys).
After this, a missing hosted value means a startup failure instead.

### 3. Where each kind of value lives

| Kind of value | Lives in | Example |
|---|---|---|
| Same everywhere | base `application.properties` | timeouts, prompt cache keys, auth policy |
| Laptop-only | `%local.` in `application.properties` | LocalStack, `localhost` CORS |
| Non-secret, per hosted env | `%dev.` / `%prod.` in `application.properties` | bucket name, R2 endpoint, OTLP `http://alloy:4317`, pool sizes |
| Secret | env var only, referenced as `${NAME}` with **no default** | DB password, R2 keys, AI key, Grafana auth |

- Non-secret hosted values are committed so they are reviewed in PRs and versioned with the image.
- Secrets flow stays: GitHub `development` Environment secrets -> `render-runtime-env.sh` ->
  root-only env file on the VM -> container. `compose.yaml` shrinks to `QUARKUS_PROFILE: dev` plus
  the env file; the `QUARKUS_*` mapping moves into `%dev.` lines such as
  `%dev.quarkus.datasource.password=${DB_PASSWORD}`.
- No defaults on hosted secrets means Quarkus refuses to start when one is missing - no partial boot.
- Hosted auth moved to Firebase in #9, so there are no OIDC values left to move.

### 4. One image, runtime profile picks the environment

The same image must be able to run as dev or prod (ADR-037 snapshot -> release). Build-time config
and `@IfBuildProfile` are baked in when CI builds the image, so:

- `%dev.` and `%prod.` may only hold **runtime** properties.
- `@IfBuildProfile` is only for laptop-vs-packaged (`"local"`), never to tell dev from prod.
- The 7 Firebase classes become `@IfBuildProfile("local")` for now. The Firebase server migration
  (`docs/plans/firebase-google-authentication-migration.md`) later removes the annotation entirely.

How values reach the container (unchanged pipeline, ADR-037 amended to promote digests):

1. GitHub Environment (`development`, later `production`) holds that environment's secrets.
2. The deploy action runs `render-runtime-env.sh` -> `runtime.env` (fails if anything is missing).
3. `runtime.env` is copied to the VM release folder over the Cloudflare Tunnel.
4. `deploy.sh` runs `docker compose --env-file runtime.env` -> container env vars.
5. Quarkus reads `QUARKUS_PROFILE` to pick `%dev.`/`%prod.` lines and `${SECRET}` references.

Prod promotes the same snapshot digest that passed on dev - it is never rebuilt.

### 5. Telemetry labels use an explicit property

Add `app.environment` (`%local.`/`%dev.`/`%prod.` set it; base has no value so startup fails if no
known profile is active). `DomainMetrics`, `AiTokenMetrics` and `UnwrappedFeatureFlags` read it
instead of `quarkus.profile`. Grafana dashboards already filter by `$environment` label values, so
they need no query change. History before the switch stays labelled `prod`/`dev`.

## Steps

1. **Spike**: confirm `QUARKUS_PROFILE=local ./gradlew :post-service:quarkusDev` keeps dev mode,
   Dev UI and hot reload, and that `@IfBuildProfile("local")` beans load. Stop and revisit if not.
2. Write ADR-055 (environment names and config layering).
3. Rewrite `application.properties` into sections: base -> `%local` -> `%dev` -> `%prod`
   (empty placeholder comment only) with a header comment explaining the four profiles.
4. Move the `service/deploy/compose.yaml` `QUARKUS_*` mappings into `%dev.` lines; set
   `QUARKUS_PROFILE: dev`; switch the service to the rendered env file.
5. Make `quarkusDev` always run `local` from `post-service/build.gradle.kts`, so `mprocs.yaml`,
   `scripts/smoke-test.sh` and direct runs need no change.
6. Swap `@IfBuildProfile("dev")` -> `"local"` on the 4 admin session classes. (#9 already moved
   the 3 Firebase token classes to `@UnlessBuildProfile("test")`.)
7. Add `app.environment`; switch the three telemetry classes to it.
8. Run the backend test suite. Any base value tests secretly relied on (S3, CORS) gets an explicit
   `%test.` line in `src/test/resources/application.properties` - tests must not inherit `%local`.
9. Add a unit test that loads `application.properties` and fails if any `%dev.`/`%prod.` value
   contains `localhost`, `127.0.0.1`, `test` S3 keys or an emulator host.
10. Update `AGENTS.md` "Environments" table (remove the translation row for the backend).
11. Deploy a snapshot to dev and check: `/api/live` healthy, a vote and a media presign work,
    Grafana shows `environment="dev"`, and the startup log shows profile `dev` with no Dev UI or
    Dev Services lines (proves launch mode NORMAL).

## Later: Kubernetes

Nothing here needs redoing. Kube injects the same contract: `QUARKUS_PROFILE` on the Deployment,
secrets from a `Secret` as env vars, non-secret values still in `application.properties` (or a
`ConfigMap` if ops needs to change them without a rebuild).

## Out of scope

- Admin SPA and Expo config naming (Expo still uses `APP_ENV=dev` for laptops). Separate plan.
- Creating any prod config, workflow or infrastructure.
