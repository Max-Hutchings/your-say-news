# CLAUDE.md

Guidance for Claude Code (and any agent) working in this repository. Read this before
starting a task so we don't re-derive the same context every session.

## What we're building

**Your Say News** is an application for a news / tech company of the same name. The product
goal:

- Users and the company publish **stories** (posts).
- Other users **vote** on those stories.
- We feed aggregated results back to users showing **how different kinds of people feel about
  a topic** — broken down by lots of key characteristics (age band, region, political
  persuasion, income range, etc.) **without exposing personally identifying information (PII)**.

That last point is the heart of the product: aggregate, anonymised sentiment by characteristic.
Never design a feature or an API that leaks individual identity alongside their vote. Keep PII
(name, email, exact DOB) separate from the characteristic data we report on.

## Tech stack

- **Backend:** Quarkus (latest release), Java 25, Gradle (Kotlin DSL) multi-module. Group id `com.yoursay`.
  Modules today: `user-service` (port 8081), `post-service` (port 8082). MVP1 keeps a **low
  service count** with strict DDD *domains* inside each (so a domain can be extracted to its own
  service later as a near-mechanical package move): `user-service` owns `user`, `usercharacteristic`,
  `social`; `post-service` owns `posts`, `votes`, `feed`. The only new service planned is
  `agent-service` (unbiased-post agent) — see `docs/plans/mvp1-roadmap.md`. Wire cross-service
  `service.*` rest-client URLs in `application.properties` as each call comes online.
- **Mobile app:** Expo / React Native (TypeScript) under `frontend/mobile/your-say-news`.
  Routing via `expo-router` (file-based, with route groups like `(protected)`).
  Styling via NativeWind/Tailwind + a shared theme under `constants/theme`.
- **Auth:** Keycloak. A realm with **real test data** is auto-imported on startup
  (`keycloak/realm-export.json`).
- **Storage:** Postgres (one DB for the app services, a separate DB for Keycloak).
  S3 via LocalStack for post video/image assets.
- **DB migrations:** Liquibase.
- **Telemetry:** Quarkus exports OpenTelemetry traces/logs and Micrometer metrics via
  `quarkus-micrometer-opentelemetry` to the local `grafana/otel-lgtm` Compose service. Grafana
  shows Prometheus metrics, Loki logs and Tempo traces on <http://localhost:3000>.
- **Run everything:** Docker Compose (`compose.yaml`).
- **Tests:** Quarkus with **Testcontainers** on the backend; **React Testing Library** on the
  frontend.

## Running the app

Use **Bun** for JavaScript package installs and scripts in this repo. Prefer `bun install` and
`bun run <script>` over npm/yarn/pnpm commands unless a specific tool explicitly requires npm.

```shell
docker compose up                     # infra: Postgres, Keycloak (+ its DB), LocalStack, Liquibase
bun install                           # one-time: installs the pinned mprocs dev runner at the repo root
bun run dev                           # all dev processes at once: both Quarkus services + Expo, in one mprocs TUI
./gradlew :user-service:quarkusDev    # OR a single service in dev mode (swap the module path)
```

`bun run dev` runs [mprocs](https://github.com/pvolok/mprocs) (config in `mprocs.yaml`), which launches
`user-service` (:8081), `post-service` (:8082) and the Expo frontend (:5173) — each in its own pane.
Infra (Compose) is assumed already up. `r` restarts the focused proc, `q` quits all.

Seed data is injected automatically on Compose startup (see DB section). Keycloak comes up with
its realm and test users already imported.

## Backend structure — Domain-Driven Design

Clear structure is non-negotiable. The package layout encodes the architecture.

```
com.yoursay.<domain>/                 <- top-level package = a DOMAIN (e.g. user, post, vote)
  <Domain>Controller.java             <- REST controllers          ┐ the domain's PUBLIC face,
  <Domain>Service.java (interface)     <- public service interfaces ├ sit at the TOP LEVEL — the
  <Domain>Dto.java                     <- DTOs crossing boundaries  ┘ ONLY things other domains touch
  model/                              <- entities, repositories                ── internal sub-package
  service/                            <- service implementations, business logic ── internal sub-package
  ...                                 <- other tech-driven sub-packages, all internal
```

Rules:

1. **Each top-level package is a domain.** (`user`, `usercharacteristic`, `post`, `vote`, …)
2. The domain's **public face sits directly at the top level of the domain package**: REST
   controllers, the public Java **interfaces** (e.g. service contracts), and the **DTOs** that
   cross domain boundaries. These are the only things other domains may touch. Do **not** nest
   them in an `interfaces/` sub-package.
3. **Everything else goes in sub-packages and must never be referenced from outside the domain
   package.** Entities, repositories, service *implementations*, mappers, etc. are private to the
   domain. Cross domains only through the top-level controllers / interfaces / DTOs, never by
   reaching into another domain's `model` or `service`.
4. Below the top level, organise sub-packages by **technical concern** (`model`, `service`, etc.)
   — tech-driven design inside the domain, domain-driven design at the top.

> Current code is mid-migration toward this. When you touch a domain, move it toward the structure
> above — controllers, public interfaces and DTOs flattened to the domain's top level, everything
> else pushed down into `model/`, `service/`, etc. — rather than adding to the old shape.

### Testing philosophy (applies to both backend and frontend)

We keep a **clear split between unit and integration tests**, and we write **both** for a domain
that has logic worth each. Optimise for **signal, not coverage** — a handful of sharp tests that
pin core logic and the edge cases where bugs live beats a wall of weak ones. Every test must be
**concise and clear**: representative data, assertions that pin **expected values** (never just
"not null" / "size > 0"), and it must actually fail if the code breaks. Do not add tests to chase a
coverage number. After writing tests, run the `test-audit` skill.

- **Unit tests** — pure domain logic and algorithms in isolation, no framework boot, no
  datastore. Fast and focused (e.g. `SentimentTallyTest`, `FeedRankerTest`, `CharacteristicSnapshotTest`
  are plain JUnit 5 over a single class). Reach for these for anything with branching/calculation.
- **Integration tests** — controllers, persistence and wiring end-to-end. `@QuarkusTest` against a
  real Postgres (and Keycloak/S3 where relevant) via **Testcontainers** — never mock the datastore.
  Cover the happy path **and** the meaningful edges (not-found/`204`, invalid input, ownership).

### Backend testing

- **Unit:** plain JUnit 5 over the class under test — no `@QuarkusTest`, so they stay fast. Put them
  in the domain's test package (e.g. `com.yoursay.votes`).
- **Integration:** `@QuarkusTest` + RestAssured against the real datastore (see `PostControllerTest`,
  `YourSayUserControllerTest`). Use `@TestSecurity` to exercise authenticated/role-gated endpoints,
  and assert a wrong-role/other-user caller is rejected where ownership applies.
- Assert on **expected values**, not just "not null". A test that still passes when the code is
  broken is worthless.
- A domain can only be integration-tested once its Liquibase table migration exists; domains that
  are still scaffolds (no migration yet) get integration tests in the stage that builds them out.

## Frontend structure

The mobile app is **Expo + expo-router**. Part of the layout is **framework-mandated** (Expo
owns it) and part is **ours to organise by domain**. Apply the same DDD instinct as the backend —
domain at the top, technical concerns inside, public face exported, internals private — but only
*outside* the directories Expo controls.

### Expo requirements (framework-mandated — do not reorganise)

Their shape is fixed by Expo/expo-router; keep them as-is:

```
app/                 <- expo-router: FILE-BASED ROUTING ONLY. Every file = a route.
  _layout.tsx        <- nested layout for its folder (navigators, providers, auth guards)
  index.tsx          <- the index route of its folder
  (group)/           <- ROUTE GROUP: groups routes WITHOUT adding a URL segment
                        (e.g. (protected) for auth-gated routes)
assets/              <- static images / fonts referenced by the app
app.json             <- base Expo config
app.config.js        <- merges app.config.dev.js / app.config.prod.js by APP_ENV
metro.config.js, babel.config.js, tailwind.config.js, global.css, tsconfig.json,
eslint.config.js, *-env.d.ts, scripts/        <- toolchain config, lives at the root
```

Rules for `app/`:

1. `app/` holds **routes and layouts only** — no domain logic, no reusable components, no API
   calls. A route file is **thin**: it imports from a feature/shared module and composes.
2. Group routes with **route groups** `(name)` (parentheses = no URL segment), named after the
   **domain/feature** they serve (`(protected)`, `(usercharacteristics)`).
3. `_layout.tsx` is where navigators, context providers and auth guards live.
4. The `@/*` alias points at the project root (`tsconfig.json`), so import as
   `@/features/...`, `@/components/ui`, `@/hooks`.

### Domain-level structure (ours — apply as much as Expo allows)

Everything that is **not** a route lives outside `app/`, organised by **domain/feature**,
mirroring the backend (a "feature" here is a domain):

```
features/<domain>/           <- a DOMAIN (auth, user-characteristics, posts, votes)
  index.ts                   <- PUBLIC FACE: the only thing routes / other features import
  components/                <- components specific to this domain        ── internal
  hooks/                     <- hooks specific to this domain             ── internal
  services/ (or api/)        <- API calls, state, keycloak/token logic    ── internal
  types.ts                   <- domain types / DTOs                       ── internal
components/
  ui/                        <- SHARED, domain-agnostic primitives (Button, Card, Input, …)
  ...                        <- other cross-cutting presentational components
hooks/                       <- SHARED cross-cutting hooks (use-color-scheme, use-theme-color)
constants/theme/             <- design tokens: colours, spacing, typography, effects
```

Rules:

1. **Domain at the top, technical concerns inside** — same shape as the backend. Each
   `features/<domain>/` owns its own components, hooks, services and types.
2. **Public face at the top** (`features/<domain>/index.ts`). Routes and other features import
   only from there; never reach into another feature's `services/` or internal components.
3. **Shared vs domain.** Truly reusable, domain-agnostic pieces go in `components/ui` (primitives),
   `hooks/` (cross-cutting) and `constants/theme` (tokens). Anything tied to one domain goes in
   that feature.
4. **One component per file.** Clear, readable, focused — if a file grows, split a piece out.
5. **Pull colours/spacing/typography from `constants/theme`**, never magic values; import shared
   UI from `@/components/ui`.

The feature layout is now in place:

```
features/
  auth/                <- index.ts · types.ts · services/ (authContext store, keycloakService,
                          UserService, requests [YsnHttpClient], tokenStorage) · hooks/ (use-fetch-with-auth)
  posts/               <- index.ts · types.ts · hooks/ (use-posts-api)
  user-characteristics/ <- index.ts · components/ (SelectableChip)
```

Token/HTTP logic (the bearer-injecting `YsnHttpClient` and `useFetchWithAuth`) lives in `auth`
and is consumed by other features through `@/features/auth` — never reach into its `services/`
directly. When you add a domain, follow the same shape and keep `app/` thin (route + layout only).

### Frontend testing

- **React Testing Library.** Verify proper rendering, user interaction, and logic/state — not
  implementation details. Test what the user sees and does.
- Same split: **unit** tests for a single component/hook's behaviour and pure helpers (e.g.
  `Button.test.tsx`); **integration** tests for a flow across components (a screen, form submission,
  navigation). Both concise, both pinning expected output, both covering the meaningful edges
  (empty/error/loading states) — not coverage-chasing.

## Database: Liquibase migrations + seeding

Migrations and seed data are **separate concerns with separate delivery**:

- Under each service's `src/main/resources/db/`, keep two folders:
  - **`migrations/`** — schema changes (DDL). The real, production-bound migrations.
  - **`seeding/`** — test/seed data inserts only.
- **Migrations run via their own dedicated container** (separate Dockerfile) — schema changes are
  deployed independently of the running services.
- **Seeding runs via its own dedicated container** (separate Dockerfile) and executes
  **automatically on `docker compose up`** so local/test environments come up with data.
- Never mix seed-data inserts into a schema changeSet.

Layout now in place (per service):

```
src/main/resources/db/
  db.changelog-master.yaml|.xml   <- includes migrations/ ONLY (used by the app at start)
  db.changelog-seed.yaml|.xml     <- includes seeding/ ONLY (used by the seeding container)
  migrations/                     <- schema changeSets
  seeding/                        <- seed-data changeSets (tagged with context "seed")
```

The app's `quarkus.liquibase.change-log` points at the master (migrations only), so running a
service no longer inserts seed data itself. Seed data is applied separately via the seed
changelog.

The dedicated containers are wired in `compose.yaml` and run on `docker compose up`:

- **`liquibase-migrate`** (`liquibase/Dockerfile.migrate`) — applies every service's master
  changelog (migrations only) once Postgres is healthy, then exits.
- **`liquibase-seed`** (`liquibase/Dockerfile.seed`) — applies every service's seed changelog
  (context `seed`) after the migration container completes successfully, then exits.

Both build from the official `liquibase/liquibase` image and share `liquibase/update.sh`, which
runs `liquibase update` per service with a per-service `--search-path` so includeAll records the
same `db/migrations/...` / `db/seeding/...` filenames the app records at `migrate-at-start` — the
container and the app never double-run a changeSet. A per-Dockerfile `.dockerignore` overrides
the repo-root one (which is tuned for the Quarkus jar builds).

## Docs

- **`docs/`** — architecture designs and reference material.
- **`docs/plans/`** — the individual implementation plans agents write before building a feature.
  When you plan a feature, write the plan here.

## Skills

- `test-audit` — audits whether tests give real signal.
- `commit-message` — commit message conventions.

---

**After writing tests for a feature, run the `test-audit` skill** to confirm the tests actually
provide signal (representative data, assertions that pin expected values, and a suite that would
genuinely fail if the code broke).


## Programming style 
Since virtual threads, reactive programming is no longer necessary for our crud applications. Default to imperitive programming with virtual threads on.
