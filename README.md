# Your Say News

Project guidance lives in [CLAUDE.md](./CLAUDE.md). Read that for architecture, conventions,
testing notes, and service layout.

## Quick Start

Start infrastructure:

```shell
docker compose up
```

In another terminal, start all services in dev mode with mprocs:

```shell
bun run dev
```

`bun run dev` first checks that the Docker Compose infrastructure is running and healthy, then
launches the configured `mprocs` workspace: `user-service`, `post-service`, and the Expo frontend.
