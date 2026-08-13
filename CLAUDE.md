## Message for AI

1. Less is more. Provide adequate detail but always avoid lots of text and over-complicated detail in responses. Be specific, be precise.
2. Never change branches without being asked to. You can complete work on a separate worktree if changes will be large but always state that when saying done

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

Topic interests are different from sentiment characteristics: they are private, account-linked
personalisation data owned by the `post-service` `topics` domain. Never put them in
`CharacteristicAnswers`, vote snapshots, public profiles or characteristic breakdown APIs.

## Tech stack

- **Backend:** Quarkus (latest release), Java 25, Gradle (Kotlin DSL). Group id `com.yoursay`.
  The sole backend deployable is `post-service` (port 8082). It keeps strict DDD domains so each can
  be extracted later as a near-mechanical package move: `user` (containing the `user`,
  `usercharacteristic` and `social` subdomains), `posts`, `votes`, `feed`, `topics`, `unwrapped`,
  and the `agents` namespace, whose role-specific official-publishing subdomains are `postagent`
  and the planned `ysnagent`. Unwrapped owns its LangChain4j implementation internally under
  `com.yoursay.unwrapped.agent` — see `docs/plans/mvp1-v2-roadmap.md`.
- **Mobile app:** Expo / React Native (TypeScript) under `frontend/mobile/your-say-news`.
  Routing via `expo-router` (file-based, with route groups like `(protected)`).
  Styling via NativeWind/Tailwind + a shared theme under `constants/theme`.
- **Auth:** Keycloak. A realm with **real test data** is imported on first startup
  (`keycloak/realm-export.json`); the `keycloak-seed-users` Compose job reconciles its users into
  an already-persisted realm on later startups.
- **Storage:** Postgres (one DB for the app, a separate DB for Keycloak).
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
bun install                           # one-time: installs the pinned mprocs dev runner at the repo root
bun run dev                           # Compose infra + post-service + Expo, in one mprocs TUI
bun run test                          # backend Testcontainers + frontend Jest, no Compose startup
./gradlew :post-service:quarkusDev    # run the backend service directly
```

`bun run dev` runs [mprocs](https://github.com/pvolok/mprocs) (config in `mprocs.yaml`), which launches
Compose with `--build`, `post-service` (:8082) and the Expo frontend (:5173), each in its own pane.
Rebuilding on startup ensures the Liquibase migration and seed images always contain the current
changelog files. Its startup script first verifies that Docker Desktop's daemon and Docker Compose
are available and that `YOUR_SAY_NEWS_GROK_API_KEY` is present. The application panes wait for
Compose to become ready. When the Compose process is
selected, `r` runs `docker compose down` and brings the stack back up with a rebuild; on other
selected processes, `r` retains mprocs' normal focused-process restart behavior. `q` quits all
processes and brings the Compose stack down. Docker volumes are preserved by both operations.
Before each application pane starts, mprocs terminates any existing listener on its assigned port
(`8082` or `5173`) so stale local dev processes do not block startup.

`bun run test` uses the separate `mprocs.test.yaml` config to run `post-service`, Expo frontend and
admin frontend tests in independent panes. Backend tests use Testcontainers and random Quarkus HTTP
ports; the test runner does not invoke Docker Compose. Select a pane and press `r` to rerun only that suite.
Completed panes remain open with an explicit PASS/FAIL result; `bun run tests` is supported as an
alias.

Seed data is injected automatically on Compose startup (see DB section). Keycloak comes up with
its realm and test users imported or reconciled from the realm export.

## Architecture decision records

Core product and architecture decisions live as ADRs in `wiki/`. Any AI agent working on this
project must add or update an ADR when a core decision is made, especially when the decision affects
data modelling, privacy, user identity, service boundaries, product rules, or third-party providers.

ADR filenames must use:

```text
ADR-<increment number>-<date>-<four-key-words>.md
```

Example: `ADR-001-2026-06-30-optional-city-place-picker.md`.

Each ADR must explain:

1. Situation
2. Options considered
3. Decision
4. Reason
5. Consequences or follow-up work, where useful

## Backend structure — Domain-Driven Design

Clear structure is non-negotiable. The package layout encodes the architecture.

```
com.yoursay.<domain>/                 <- top-level package = a DOMAIN (e.g. user, post, vote)
  <Domain>Controller.java             <- REST controllers          ┐ the domain's PUBLIC face,
  <Domain>Service.java (interface)     <- public service interfaces ┘ sit at the TOP LEVEL
  dto/                                <- DTOs crossing boundaries — also PUBLIC
    <Domain>Dto.java
  model/                              <- entities, repositories                ── internal sub-package
  service/                            <- service implementations, business logic ── internal sub-package
  ...                                 <- other tech-driven sub-packages, all internal
```

Rules:

1. **Each top-level package is a domain.** (`user`, `usercharacteristic`, `post`, `vote`, …)
2. The domain's **public face** consists of its top-level package and its `dto` sub-package.
   REST controllers and public Java **interfaces** (e.g. service contracts) sit directly at the
   top level. DTOs sit in `dto/` so the domain's chain of events is immediately visible without
   DTO declarations obscuring it. Do **not** nest controllers or public interfaces in an
   `interfaces/` sub-package.
3. **Every sub-package except `dto` is internal and must never be referenced from outside the
   domain package.** Entities, repositories, service *implementations*, mappers, etc. are private
   to the domain. Cross domains only through top-level controllers/interfaces or types in `dto`,
   never by reaching into another domain's `model`, `service`, or other internal package.
4. Below the top level, organise sub-packages by **technical concern** (`model`, `service`, etc.)
   — tech-driven design inside the domain, domain-driven design at the top.
5. **REST controller methods must not return the generic JAX-RS `Response` type.** Declare the
   concrete DTO response contract (including collection or asynchronous wrappers where needed) and
   express HTTP status codes with annotations such as `@ResponseStatus`. Returning `Response`
   weakens the public Java/API contract and is prohibited.

The deliberate exception is `com.yoursay.agents`: it is a namespace for role-specific official
publishing agent subdomains. `postagent` and the planned `ysnagent` are separate domain boundaries
beneath it, and each follows the public-face/internal-subpackage rules above independently.
`com.yoursay.unwrapped` is instead a normal top-level domain; its `agent` child is an internal
technical package, not another domain.

> Current code is mid-migration toward this. When you touch a domain, move it toward the structure
> above — controllers and public interfaces at the domain's top level, DTOs in `dto/`, and
> everything else pushed down into `model/`, `service/`, etc. — rather than adding to the old shape.

### Testing philosophy (applies to both backend and frontend)

We keep a **clear split between unit and integration tests**, and we write **both** for a domain
that has logic worth each. Optimise for **signal, not coverage** — a handful of sharp tests that
pin core logic and the edge cases where bugs live beats a wall of weak ones. Every test must be
**concise and clear**: representative data, assertions that pin **expected values** (never just
"not null" / "size > 0"), and it must actually fail if the code breaks. Do not add tests to chase a
coverage number. After writing tests, run the `test-audit-for-after-changing-tests` skill.

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

**Authoritative visual design** (the "editorial" language — paper/ink/lime palette, teal=Agree/coral=Disagree, Newsreader + Schibsted Grotesk + Spline Sans Mono) lives in `frontend/mobile/your-say-news/constants/theme/editorial.ts` — consume via `getEditorial(isDark)` / `EditorialFont`, not the legacy enterprise `Colors`/`BrandColors` (blue).

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

## Database: central Liquibase migrations + seeding

Migrations and seed data are **separate concerns with separate delivery**:

- Keep the changelog outside application services under `liquibase/changelog/db/`:
  - **`migrations/`** — schema changes (DDL), plus **reference data** (see below). The real,
    production-bound migrations.
  - **`seeding/`** — test/seed data inserts only.
- User-domain migrations and fixtures live in the sibling `user-migrations/` and
  `user-seeding/` folders in the same central tree.
- **Migrations run via their own dedicated container** (separate Dockerfile) — schema changes are
  deployed independently of the running service.
- **Seeding runs via its own dedicated container** (separate Dockerfile) and executes
  **automatically on `docker compose up`** so local/test environments come up with data.
- Never mix seed-data inserts into a schema changeSet.
- **Reference data is not seed data.** Rows the application is broken without in *every* environment
  — a controlled catalogue such as the topic taxonomy, effectively an enum held in a table — belong
  in `migrations/`, because that is the changelog both `migrate-at-start` and the `liquibase-migrate`
  container run everywhere. `seeding/` only ever reaches developer machines. Give reference data its
  own changeSet next to the DDL changeSet that creates its table, never inside it. Seed data (sample
  posts, test votes, fixture users) still goes to `seeding/` and nowhere else. See
  `ADR-043` for the decision.

Layout now in place:

```
liquibase/changelog/db/
  db.changelog-master.yaml|.xml   <- includes migrations/ ONLY (used by the app at start)
  db.changelog-seed.yaml|.xml     <- includes seeding/ ONLY (used by the seeding container)
  user-migrations/                <- schema changeSets for the user domains
  migrations/                     <- schema changeSets
  user-seeding/                   <- seed-data changeSets for the user domains
  seeding/                        <- seed-data changeSets (tagged with context "seed")
```

Gradle adds `liquibase/changelog` as an external `post-service` resource directory, so the app and
the dedicated containers consume the same files without making the changelog part of a service's
source tree. The app's `quarkus.liquibase.change-log` points at the master (migrations only), so
running `post-service` never inserts seed data itself. Seed data is applied separately via the seed
changelog.

The dedicated containers are wired in `compose.yaml` and run on `docker compose up`:

- **`liquibase-migrate`** (`liquibase/Dockerfile.migrate`) — applies `post-service`'s master
  changelog (migrations only) once Postgres is healthy, then exits.
- **`liquibase-seed`** (`liquibase/Dockerfile.seed`) — applies `post-service`'s seed changelog
  (context `seed`) after the migration container completes successfully, then exits.

Both build from the official `liquibase/liquibase` image and share `liquibase/update.sh`, which
runs `liquibase update` with the central changelog search path so includeAll records the same
`db/migrations/...` / `db/seeding/...` filenames the app records at `migrate-at-start` — the
container and the app never double-run a changeSet. A per-Dockerfile `.dockerignore` overrides
the repo-root one (which is tuned for the Quarkus jar build).

## Docs

- **`docs/`** — architecture designs and reference material.
- **`docs/plans/`** — the individual implementation plans agents write before building a feature.
  When you plan a feature, write the plan here.

## Skills

- `test-audit-for-after-changing-tests` — audits whether tests give real signal.
- `commit-message` — commit message conventions.
- `instrument-app-observability` — defines required metrics, logs, traces, error classification and
  Grafana dashboards. **Before adding or changing production code, read and follow
  `.agents/skills/instrument-app-observability/SKILL.md`.** Include the required observability in
  the same change as the application behaviour.

Side note: Don't do work on a new branch unless instructed by a user or a designated skill

---

**After writing tests for a feature, run the `test-audit-for-after-changing-tests` skill** to confirm the tests actually
provide signal (representative data, assertions that pin expected values, and a suite that would
genuinely fail if the code broke).


## Programming style
Since virtual threads, reactive programming is no longer necessary for our crud applications. Default to imperative programming with virtual threads on.

### Method design and single responsibility

- Complex workflow methods should read as a clear summary. Extract meaningful business rules, transformations, calculations, grouping, filtering and sorting into precisely named private methods.
- Keep each method at one level of abstraction and give it one clear responsibility. Avoid vague names such as `processVotes`, `handleResults` or `buildData`.
- Do not extract obvious expressions, accessors or constructors. A private method must remove meaningful detail, not merely make the calling method shorter.

Avoid placing the entire workflow and all implementation details in one method:

```java
public Report build(List<Vote> votes, int threshold) {
    List<Vote> sortedVotes = votes.stream()
            .sorted(Comparator.comparing(Vote::createdAt))
            .toList();

    Map<Long, Long> counts = new LinkedHashMap<>();
    sortedVotes.forEach(vote ->
            counts.merge(vote.optionId(), 1L, Long::sum));

    List<Result> results = counts.entrySet().stream()
            .filter(entry -> entry.getValue() >= threshold)
            .map(entry -> new Result(
                    entry.getKey(),
                    entry.getValue(),
                    percentage(entry.getValue(), votes.size())
            ))
            .toList();

    return new Report(votes.size(), results);
}
```

Prefer extracting the meaningful operations while leaving obvious expressions and construction visible:

```java
public Report build(List<Vote> votes, int threshold) {
    List<Vote> sortedVotes = sortVotesByCreationTime(votes);
    Map<Long, Long> voteCountsByOption = countVotesByOption(sortedVotes);
    Map<Long, Long> surfacedVoteCounts =
            selectVoteCountsAtOrAboveThreshold(voteCountsByOption, threshold);
    List<Result> results =
            createOptionResults(surfacedVoteCounts, votes.size());

    return new Report(votes.size(), results);
}

private List<Vote> sortVotesByCreationTime(List<Vote> votes) {
    return votes.stream()
            .sorted(Comparator.comparing(Vote::createdAt))
            .toList();
}

private Map<Long, Long> countVotesByOption(List<Vote> votes) {
    Map<Long, Long> counts = new LinkedHashMap<>();

    votes.forEach(vote ->
            counts.merge(vote.optionId(), 1L, Long::sum));

    return counts;
}

private Map<Long, Long> selectVoteCountsAtOrAboveThreshold(
        Map<Long, Long> voteCountsByOption,
        int threshold
) {
    return voteCountsByOption.entrySet().stream()
            .filter(entry -> entry.getValue() >= threshold)
            .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (existing, replacement) -> existing,
                    LinkedHashMap::new
            ));
}

private List<Result> createOptionResults(
        Map<Long, Long> voteCountsByOption,
        long totalVoteCount
) {
    return voteCountsByOption.entrySet().stream()
            .map(entry -> new Result(
                    entry.getKey(),
                    entry.getValue(),
                    percentage(entry.getValue(), totalVoteCount)
            ))
            .toList();
}

private double percentage(long count, long total) {
    return total == 0 ? 0.0 : 100.0 * count / total;
}
```
