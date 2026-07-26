# Development deployment

This directory is the remote, production-shaped Compose root. The repository-root `compose.yaml`
continues to own local development substitutes such as PostgreSQL, Keycloak, LocalStack and LGTM.

The steady runtime is:

- one `post-service` JVM container;
- Grafana Alloy receiving OTLP on the private Compose network; and
- Cloudflare Tunnel publishing the API without a public VM listener.

Liquibase is a one-shot release step. Seed data is deliberately not deployed.

## Runtime contract

Copy `env.example` to the ignored `runtime.env` only for local validation. In the real deployment,
CI renders `runtime.env` from the protected GitHub `development` Environment, transfers it over
the private Tailscale path and sets mode `0600`.

The API and migration images must be references by digest. Alloy and cloudflared must use reviewed,
pinned versions before the first deployment. Never commit populated credentials.
Compose maps the runtime file into per-service allowlists: for example, the tunnel receives only
its tunnel token and Alloy receives only its Grafana publishing settings.

The Compose file binds the API to VM loopback on port 8082. Cloudflared reaches it over the private
Compose network; the host firewall does not need an inbound API port.

## Operator commands

From this directory:

```shell
cp env.example runtime.env
docker compose --env-file runtime.env config
./scripts/deploy.sh
```

`deploy.sh` pulls the selected artifacts, runs forward-only migrations, starts the steady services
and checks `/live`. Set `PUBLIC_HEALTH_URL=https://api.example.test/live` to include the public
Cloudflare path.

Automatic application rollback is not implemented in this skeleton. The release workflow must
retain the previous API digest and restore it if the post-deploy health check fails; it must never
attempt to reverse a database migration.
