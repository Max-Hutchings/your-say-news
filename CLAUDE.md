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
  Modules today: `user-service` (port 8081), `post-service` (port 8082). More services to come
  (e.g. characteristic, vote) — see the `service.*` URLs in `application.properties`.
- **Mobile app:** Expo / React Native (TypeScript) under `frontend/mobile/your-say-news`.
  Routing via `expo-router` (file-based, with route groups like `(protected)`).
  Styling via NativeWind/Tailwind + a shared theme under `constants/theme`.
- **Auth:** Keycloak. A realm with **real test data** is auto-imported on startup
  (`keycloak/realm-export.json`).
- **Storage:** Postgres (one DB for the app services, a separate DB for Keycloak).
  S3 via LocalStack for post video/image assets.
- **DB migrations:** Liquibase.
- **Run everything:** Docker Compose (`compose.yaml`).
- **Tests:** Quarkus with **Testcontainers** on the backend; **React Testing Library** on the
  frontend.

## Running the app

```shell
docker compose up                     # infra: Postgres, Keycloak (+ its DB), LocalStack, Liquibase
npm install                           # one-time: installs the pinned mprocs dev runner at the repo root
npm run dev                           # all dev processes at once: both Quarkus services + Expo, in one mprocs TUI
./gradlew :user-service:quarkusDev    # OR a single service in dev mode (swap the module path)
```

`npm run dev` runs [mprocs](https://github.com/pvolok/mprocs) (config in `mprocs.yaml`), which launches
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

### Backend testing

- Use **Quarkus + Testcontainers** so tests run against a real Postgres (and Keycloak/S3 where
  relevant), not mocks of the datastore.
- Use `@TestSecurity` to exercise authenticated/role-gated endpoints (see
  `YourSayUserControllerTest`).
- Assert on **expected values**, not just "not null". A test that still passes when the code is
  broken is worthless.

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
