# ADR-055: Environment config and secret injection

**Date:** 2026-10-02
**Status:** Accepted

## Situation

We have three environments (defined in `AGENTS.md` "Environments"):

- **local** - a developer machine.
- **dev** - the hosted development server at `https://dev.yoursaynews.com/api` (Hetzner VM behind
  Cloudflare Tunnel, ADR-035/036).
- **prod** - not built yet.

`post-service` config did not use these words correctly:

- Quarkus `%dev` meant a laptop, and the hosted dev server ran `QUARKUS_PROFILE=prod`.
- Telemetry used `quarkus.profile` as its `environment` label, so Grafana showed the hosted dev
  server as `prod` and laptops as `dev`.
- Un-prefixed (base) config held laptop values: LocalStack S3 with `test/test` keys, `localhost`
  CORS, migrate-at-start, Quinoa serving the admin UI. The hosted server only worked because
  ~25 `QUARKUS_*` overrides in `service/deploy/compose.yaml` replaced them. A missing override fell
  back silently to laptop values.
- Hosted config was spread across `application.properties`, `service/deploy/compose.yaml`,
  `render-runtime-env.sh` and the workflow `env:` block.
- `QUARKUS_HTTP_ROOT_PATH=/api` was set at runtime, but `quarkus.http.root-path` is a build-time
  property in Quarkus 3.36 (`VertxHttpBuildTimeConfig`), so Quarkus fixes it when the image is
  built and does not apply the runtime value.

We also need the image tested on dev to be the exact image that runs in prod (ADR-037 amended), and
the approach must carry over to Kubernetes without rework.

## Options considered

1. **Keep Quarkus default profile names and document a translation table.** No code change, but
   every reader keeps guessing wrong about `%dev.`, and Grafana labels stay wrong.
2. **Profile names match environment names, base is hosted-safe, secrets injected at runtime.**
3. **One properties file per environment** (`application-dev.properties` etc.) mounted into the
   container. Splits the full picture across files and makes the image depend on a mount.
4. **External config server / secret manager now** (Vault, Doppler, cloud secret store). More
   infrastructure than one VM needs; GitHub Environments already store the secrets.

## Decision

Option 2.

### Profiles

| Profile | Runs where | Set by |
|---|---|---|
| `local` | `quarkusDev` on a laptop | `post-service/build.gradle.kts` passes `-Dquarkus.profile=local` to every `quarkusDev` run |
| `test` | `@QuarkusTest` | Quarkus default |
| `dev` | hosted dev VM | `QUARKUS_PROFILE=dev` in `runtime.env` |
| `prod` | future | `QUARKUS_PROFILE=prod` in that environment's `runtime.env` |

Profile is separate from Quarkus launch mode. `quarkusDev` = launch mode DEVELOPMENT (hot reload,
Dev UI, Dev Services). The image runs `java -jar` = launch mode NORMAL, always. Running the image
with profile `dev` only selects `%dev.` lines; it does not turn on dev-mode behaviour. Checked
against Quarkus 3.36.2: no Quarkus/Quarkiverse runtime jar we use ships `%dev.`/`%prod.` defaults,
and mode-dependent defaults such as `quarkus.http.host` are keyed on `LaunchMode`.

### Where each value lives

| Kind of value | Lives in | Example |
|---|---|---|
| Same in every environment | base `application.properties` | timeouts, prompt cache keys, auth policy |
| Laptop-only | `%local.` | LocalStack, Compose Postgres, `localhost` CORS, Firebase emulator |
| Test-only | `src/test/resources/application.properties` `%test.` | Testcontainers, test CORS origin |
| Non-secret, per hosted env | `%dev.` / `%prod.` | R2 endpoint, bucket name, `http://alloy:4317` |
| Secret | env var only, referenced as `${NAME}` with **no default** | DB password, R2 keys, AI key |

Rules:

1. **Base is hosted-safe.** Base values must be right on a server or fail closed (CORS off,
   migrate-at-start off, admin UI not served). Laptop conveniences opt in
   under `%local.`. Tests pin what they need under `%test.` and never inherit `%local.`.
2. **Secrets have no defaults in hosted profiles.** A missing secret stops startup.
3. **`%dev.` / `%prod.` hold runtime properties only.** Build-time properties
   (`quarkus.http.root-path`, `quarkus.quinoa.*`, `quarkus.datasource.db-kind`,
   `quarkus.hibernate-orm.*`) are baked in when CI builds the image, so they live in base (shared by
   dev and prod) and only `%local.`/`%test.` may override them.
4. **Build-profile annotations never tell dev from prod.** Only `@IfBuildProfile("local")`
   (laptop-only beans such as the admin cookie session) and `@UnlessBuildProfile("test")` (beans
   tests replace, such as the Firebase token verifier) are allowed.
5. **`app.environment` names the environment for telemetry.** `%local.`, `%dev.` and `%test.` set
   it; base does not. An image started without a known profile (Quarkus falls back to `prod`, which
   has no config yet) fails at startup instead of running with the wrong settings.

### Secret injection (VM today)

1. Secrets live in GitHub repository secrets prefixed per environment (`DEV_*` now, `PROD_*`
   later). This private GitHub Free repository has no Environment-scoped secrets; the deploy
   workflow maps each prefixed secret to the plain name the app expects.
2. The deploy action runs `service/deploy/scripts/render-runtime-env.sh`, which validates every
   required value and writes `runtime.env` (mode `0600`) including `QUARKUS_PROFILE`.
3. `runtime.env` is copied over the Cloudflare Tunnel into the VM's release folder.
4. `deploy.sh` runs `docker compose --env-file runtime.env`. `compose.yaml` passes through only the
   named variables the service needs; it holds no Quarkus property mapping.
5. Quarkus selects `%dev.` lines from `QUARKUS_PROFILE` and resolves `${SECRET}` references.

The image contains every profile's non-secret config and no secrets. The same digest runs as dev or
prod purely from what step 2 injects.

### Kubernetes later

The contract stays the same: `QUARKUS_PROFILE` set on the Deployment, secrets from a Kubernetes
`Secret` exposed as env vars, non-secret values still in `application.properties` (or a `ConfigMap`
if ops needs to change a value without a new image).

## Reason

- Words in config now mean what they mean in conversation, so `%dev.` is never misread.
- Fail-closed base removes a whole class of silent misconfiguration on servers.
- One file (`application.properties`) shows every environment's non-secret config and is reviewed
  in PRs; secrets never enter the repo or the image.
- Keeping build-time config identical for dev and prod is what makes "promote the tested digest"
  (ADR-037) safe.

## Consequences and follow-up work

- Grafana history before this change keeps the old `environment` labels (`prod` = hosted dev,
  `dev` = laptop). New data uses `local` / `dev`.
- The hosted server now actually serves under `/api` (build-time base value); `%local.`/`%test.`
  keep `/`.
- `EnvironmentConfigContractTest` guards the rules: no laptop values or fixtures in base or hosted
  profiles (including multi-profile keys), only allow-listed runtime properties under
  `%dev.`/`%prod.`, raw base values hosted-safe, no defaults on any hosted `${...}` reference, the
  `%dev.` secret names exactly match what `compose.yaml` passes and `render-runtime-env.sh` writes,
  `app.environment` set per profile, and `@IfBuildProfile` only ever targets `"local"`.
- `deploy-scripts.test.sh` asserts the post-service container receives exactly the allow-listed
  variables, so nothing (a Quarkus mapping, a feature flag, a threshold) can override
  `application.properties` from Compose.
- Vote suppression is a product decision and is not set by this ADR. Values are unchanged from
  before it: base `0`, hosted dev `5` (previously injected by Compose, now `%dev.`). No test pins
  either value.
- Hosted authentication uses the Firebase Admin SDK (`FirebaseAuthenticationMechanism`), the same
  code that already verified emulator tokens on laptops. `quarkus-oidc` is removed in favour of
  `quarkus-security`. `FIREBASE_PROJECT_ID` stays an injected value rather than a `%dev.` line
  because the deploy action also uses it to validate the service-account JSON, so it has one source.
  The Firebase SDK reads `GOOGLE_APPLICATION_CREDENTIALS` itself; compose sets it to the mounted
  credential path.
- The admin web UI and its cookie session stay laptop-only (`@IfBuildProfile("local")`). Serving it
  on dev is a separate decision.
- Admin SPA and Expo environment naming are not covered here.
- Creating `%prod.` config waits until prod is built.
