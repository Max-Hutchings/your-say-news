# Development deployment

This directory is the remote, production-shaped Compose root. The repository-root `compose.yaml`
continues to own local development substitutes such as PostgreSQL, Keycloak, LocalStack and LGTM.

The steady runtime is:

- one `post-service` JVM container;
- Grafana Alloy receiving OTLP on the private Compose network.

The infrastructure bootstrap installs `cloudflared` as a separate host service. Keeping the
connector outside application Compose lets the deployment workflow reach the VM before any
application containers exist. It publishes the loopback-only API listener and the local SSH
daemon without opening a public inbound port.

Liquibase is a one-shot release step. Seed data is deliberately not deployed.

## Application workflow

`.github/workflows/dev-app.yml` is the concrete development application pipeline. On every
relevant branch push it:

1. tests `post-service` with GraalVM 25 and tests this deployment contract;
2. builds the post-service and migration image archives in separate parallel jobs without pushing;
3. publishes both commit snapshots to the development GHCR packages; and
4. records their immutable digests in a checksum-sealed, one-day deployment artifact.

The workflow shows `Test`, parallel `Build · Post-service image` and `Build · Migration image`,
`Publish · Commit snapshot`, and `Deploy · Explicit manual deployment` as separate jobs. The build
jobs create archives but cannot push; only the publish job writes GHCR and seals the two digests.
Supply the successful snapshot run ID, its full commit SHA and the exact phrase
`deploy development`. The selected branch must still
resolve to that SHA. The deployment job verifies the source run and artifact, connects to the
`deploy` user through Cloudflare Access SSH, runs the migration, starts the application and Alloy,
checks the private and public health routes, then activates the release symlink. The VM never
checks out this repository.

GitHub's workflow token publishes and temporarily pulls the two private snapshot packages; no
Nexus server or long-lived registry token is needed. The first successful push creates
`your-say-news-post-service-snapshot` and `your-say-news-migrations-snapshot`; confirm both are
private and inherit access from this repository. The unsuffixed package names are reserved for a
future production workflow driven only by approved version tags (ADR-037).

Snapshot image tags and Actions artifacts use the seven-character commit form, for example
`sha-a1b2c3d` and `application-development-snapshot-a1b2c3d`. OCI labels, sealed metadata and manual
authorization retain the full 40-character SHA, and the VM deploys the exact image digest.

### Repository secrets

This private GitHub Free repository uses repository secrets rather than GitHub Environments:

| Secret | Purpose |
| --- | --- |
| `CLOUDFLARE_ACCESS_CLIENT_ID`, `CLOUDFLARE_ACCESS_CLIENT_SECRET` | Scoped service token for the SSH Access application |
| `DEV_SSH_PRIVATE_KEY`, `DEV_SSH_KNOWN_HOSTS` | Dedicated deploy key and pinned host key; host-key checking never uses `accept-new` |
| `DEV_DB_JDBC_URL`, `DEV_DB_REACTIVE_URL` | Aiven application database endpoints with required TLS parameters |
| `DEV_DB_MIGRATION_USERNAME`, `DEV_DB_MIGRATION_PASSWORD` | Migration-only Aiven user |
| `DEV_DB_USERNAME`, `DEV_DB_PASSWORD` | Runtime-only Aiven user |
| `DEV_OIDC_AUTH_SERVER_URL`, `DEV_OIDC_CLIENT_ID` | Approved development OIDC configuration |
| `DEV_R2_ACCESS_KEY_ID`, `DEV_R2_SECRET_ACCESS_KEY` | R2 credentials restricted to the media bucket |
| `DEV_XAI_API_KEY` | Server-side xAI credential |
| `DEV_UNWRAPPED_API_KEY` | Optional separate Unwrapped credential; xAI is used when omitted |
| `DEV_GRAFANA_CLOUD_OTLP_ENDPOINT`, `DEV_GRAFANA_CLOUD_OTLP_AUTHORIZATION` | Grafana Cloud OTLP destination and authorization header |

The workflow currently pins the development SSH hostname, public health URL, EU R2 endpoint and
media bucket because this is the concrete development deployment. Change them with the
corresponding reviewed infrastructure contract, not through secrets.

Before the first infrastructure apply, create a dedicated Hetzner SSH key for GitHub deployment
and include its name in `hcloud_ssh_key_names`; cloud-init can install only keys supplied when the
VM is created. Capture the resulting host key through the trusted Hetzner console for
`DEV_SSH_KNOWN_HOSTS`. The Cloudflare zone, Tunnel connector and SSH Access application/service
token must also be working before the first application deployment.

## Runtime contract

Copy `env.example` to the ignored `runtime.env` only for local validation. In the real deployment,
CI renders `runtime.env` from repository secrets, transfers it over the Cloudflare
Access-protected SSH path and sets mode `0600`.

The API and migration images must be references by digest. Alloy must use a reviewed, pinned
version before the first deployment. Never commit populated credentials.
Compose maps the runtime file into per-service allowlists. The host Tunnel token is installed
separately as `/etc/cloudflared/tunnel.env` with root ownership and is never written to the
application runtime file.

The Compose file binds the API to VM loopback on port 8082 and gives the remote Quarkus profile the
`/api` root path. The host-level `cloudflared` service reaches that loopback listener; the host
firewall does not need an inbound API port. Development intentionally has no web frontend:
`https://dev.yoursaynews.com/api/*` is the public backend contract and the hostname root may return
`404`.

## Operator commands

From this directory:

```shell
cp env.example runtime.env
docker compose --env-file runtime.env config
./scripts/deploy.sh
```

`deploy.sh` pulls the selected artifacts, runs forward-only migrations with the dedicated
migration user, starts the steady services with the lower-privilege runtime user and checks
`/api/live`. Set `PUBLIC_HEALTH_URL=https://api.example.test/api/live` to include the public
Cloudflare path.

`release.sh` changes the `current` symlink only after a healthy deployment. If a later release
fails and a previous release exists, it restores the previous application and Alloy containers.
It deliberately never reverses or reruns the failed release's database migration, so every
migration must remain backward compatible with the preceding application release.
