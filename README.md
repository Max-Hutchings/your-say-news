# Your Say News

Project guidance lives in [CLAUDE.md](./CLAUDE.md). Read that for architecture, conventions,
testing notes, and service layout.

## Prerequisites

Install these before setting up the project:

- **Git**
- **GraalVM for JDK 25** — ensure `JAVA_HOME` points to it and `java --version` reports
  GraalVM and Java 25. The Gradle build is pinned to the Java 25 toolchain.
- **Bun** — use the current stable release. Bun is the package manager and script runner used
  throughout the repository.
- **Node.js 20** — required by the Expo/Jest frontend toolchain; dependencies must still be
  installed with Bun.
- **Docker Desktop** — use a current release, start the Docker daemon, and make sure its bundled
  Docker Compose v2 command is available. Compose runs Postgres, the Firebase Auth Emulator, LocalStack, Liquibase,
  and the local Grafana telemetry stack; backend tests also use Docker through Testcontainers.
- **AI provider API key** - OpenAI is the default. Export `OPENAI_API_KEY` before running
  `bun run dev`. To use Grok instead, set `AGENT_PROVIDER=grok` and export
  `YOUR_SAY_NEWS_GROK_API_KEY`. The development preflight validates the selected provider.
- **Bash, `curl`, and `lsof`** — used by the development and smoke-test scripts. These are
  included with macOS; on other systems, install them and ensure they are on `PATH`.

You do **not** need to install Gradle, Postgres, Firebase CLI, LocalStack, Liquibase, Grafana, or
`mprocs` separately. The Gradle wrapper, Docker Compose, and Bun dependencies provide them.

For native mobile development, also install the platform tooling you intend to use:

- **iOS:** Xcode and an iOS Simulator (macOS only), or Expo Go on a physical device.
- **Android:** Android Studio with the Android SDK and an emulator, or Expo Go on a physical
  device.

The optional Playwright smoke tests require their bundled Chromium browser. Install it during
first-time setup with `bunx playwright install chromium`.

## First-time setup

Install each JavaScript application's locked dependencies:

```shell
bun install
(cd frontend/mobile/your-say-news && bun install)
(cd post-service/src/main/webui && bun install)
```

Confirm the main tools are available:

```shell
java --version
bun --version
node --version
docker info
docker compose version
```

## Quick Start

```shell
bun run dev
```

Remote development-environment infrastructure and deployment operations live under
[`service/`](./service/).
