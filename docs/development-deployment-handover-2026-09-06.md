# Development deployment handover

Date: 2026-09-06  
Repository: `Max-Hutchings/your-say-news`  
Working branch: `feat/add-infra`  
Remote branch commit at handover: `ff34e438975814c58ee12846d8bd2261778d90b4`

This is the current operational handover for the development environment. It supersedes
`devops-travel-handoff-2026-09-05.md`. It deliberately contains no passwords, private keys, API
tokens, Cloudflare Tunnel token, Firebase private key, or R2 secret.

## Read this first

The infrastructure is no longer just a plan. During the setup session:

- the Hetzner VM completed cloud-init;
- the Cloudflare Tunnel token was installed on the VM and the connector was started;
- `ssh-dev.yoursaynews.com` was used successfully to SSH to the VM as `deploy`;
- SSH socket/service and port 22 were confirmed active on the VM;
- Aiven PostgreSQL exists, the application/migration users exist, and the required SQL grants were
  applied and verified;
- the Firebase development project, Android application and Admin SDK credential exist;
- the Expo/EAS project is linked and an Android build reached the EAS Builds page;
- the required GitHub deployment secrets were populated, with a placeholder OpenAI key; and
- the application test and deployment-contract jobs now pass.

The application has **not yet been deployed**. The current blocker is the post-service snapshot
build described in [Current CI blocker](#current-ci-blocker-quinoabun). The final outcome of the
earlier EAS Android build was not checked before this handover.

Development remains synthetic-data-only. Production infrastructure is intentionally absent.

## Branch comparison

The useful comparison is against the current remote main branch, not the stale local `main` ref:

```text
HEAD:        ff34e438975814c58ee12846d8bd2261778d90b4
origin/main: 6da20a61b54c436bd4928f7caa128a5f016f8d3c
local main:  60065b0a8e0607f199aaf014d4dbbdfee6420a20
```

At handover, `git diff --stat origin/main...HEAD` reports 43 changed files, 855 insertions and 144
deletions. The deployment-relevant differences are:

- Terraform now selects an Aiven Free-compatible DigitalOcean/UpCloud region and pins development
  to `do-lon`.
- The Hetzner host is IPv6-only and its bootstrap repairs SSH socket startup, installs Docker and
  creates the dormant host-level Tunnel service.
- Remote authentication uses Firebase Admin credentials rather than the old transitional auth
  inputs.
- Expo has an EAS project, Android package/config, development-client and development-store build
  profiles, hosted Firebase configuration, and `expo-updates` wiring.
- The remote Compose release accepts Aiven, Firebase, R2, AI and Grafana credentials; it binds the
  API only to VM loopback and publishes `/api` through the Tunnel.
- Deployment tests now make the public health URL deterministic.
- Both image archive jobs now request a `docker-container` Buildx builder, fixing the earlier
  `Docker exporter is not supported for the docker driver` error.

Reproduce the focused comparison with:

```bash
git fetch origin
git diff --stat origin/main...feat/add-infra
git diff --name-status origin/main...feat/add-infra
```

Do not change branches casually: the application workflow requires a manual deployment to be
dispatched from the exact commit that produced the successful snapshot.

## Architecture and traffic flow

```text
Android app (EAS/Google Play)
    | Firebase Google sign-in -> Firebase ID token
    v
https://dev.yoursaynews.com/api/*
    | Cloudflare DNS + outbound Tunnel
    v
Hetzner VM 127.0.0.1:8082
    | Docker Compose: post-service + Grafana Alloy
    +--> Aiven PostgreSQL (managed; PostgreSQL is not installed on the VM)
    +--> Cloudflare R2 media bucket
    +--> Firebase Admin token verification
    +--> Grafana Cloud through Alloy

Operator/GitHub Actions
    | Cloudflare Access-protected SSH
    v
ssh-dev.yoursaynews.com -> Tunnel -> VM localhost:22 -> deploy user
```

The VM has no purchased public IPv4 and both Hetzner and UFW deny public inbound traffic. Normal
API and SSH access must use Cloudflare Tunnel. The Hetzner browser console is the break-glass path.

## Resource inventory

| Platform | Development resources | Source of truth / notes |
| --- | --- | --- |
| Hetzner Cloud | Ubuntu 24.04 CX23 server `your-say-news-development` in `nbg1`; deny-ingress firewall; two registered SSH public keys | `service/infra/environments/development/development.tfvars` |
| Aiven | Project/service `your-say-news-development`; PostgreSQL `free-1-1gb` in `do-lon`; database `your_say_news`; users `ysn_migration` and `ysn_runtime` | PostgreSQL is managed by Aiven, not Compose |
| Cloudflare DNS/Tunnel | Tunnel `your-say-news-development`; `dev.yoursaynews.com -> http://localhost:8082`; `ssh-dev.yoursaynews.com -> ssh://localhost:22`; final 404 ingress rule | Tunnel/config/DNS are Terraform-managed |
| Cloudflare Access | Self-hosted/Public DNS SSH application for `ssh-dev.yoursaynews.com`; human Allow policy; CI Service Auth policy and service token | Access identities/policies are dashboard-managed and deliberately outside Terraform state |
| Cloudflare R2 | Private EU buckets `your-say-news-media-development` and `your-say-news-backup-development`; backup lifecycle for incomplete, daily and monthly objects | Media runtime key is separate from the Terraform API token |
| HCP Terraform | Organisation `your-say-news`; workspace `your-say-news-development`; remote state/locking with local execution in GitHub Actions | Saved plans expire after one day |
| GitHub | Repository Actions secrets; snapshot GHCR packages `your-say-news-post-service-snapshot` and `your-say-news-migrations-snapshot`; sealed one-day deployment artifact | Private GitHub Free account uses repository secrets rather than Environment secrets |
| Firebase | Project `your-say-news-development`; Android app package `com.yoursaynews.app`; Google authentication; Firebase Admin service account | Confirm all required signing fingerprints before Play testing |
| Expo/EAS | Account/owner `your-say-news`; project `your-say-news`; EAS project ID `4e62f40e-0837-4caf-9e26-e3d33e77e4ad`; development environment/channel | Native builds are done by EAS, not the backend workflow |
| Google Play | Application uses package `com.yoursaynews.app` | Internal-track submission is still a later/manual step unless already completed externally |
| Grafana Cloud | OTLP endpoint and authorization credential used by Alloy | Stack name and alerting/restore evidence were not recorded in this session |

The committed non-secret identifiers, regions, routes, bucket lifecycle and resource switches are
in `service/infra/environments/development/development.tfvars`. All four development resource
groups are enabled. Do not put passwords or provider tokens in that file.

## GitHub repository secrets

The following names were present at the end of setup:

| Secret | Where the value comes from | Required format |
| --- | --- | --- |
| `TF_API_TOKEN` | HCP Terraform user/team token | Raw token |
| `HCLOUD_TOKEN` | Hetzner project API token | Raw token |
| `AIVEN_TOKEN` | Aiven project/account token | Raw token; not a database password |
| `CLOUDFLARE_API_TOKEN` | Cloudflare API Tokens | Raw narrowly scoped Terraform token |
| `CLOUDFLARE_ACCESS_CLIENT_ID` | Cloudflare Zero Trust service token | Copy only the raw Client ID value, not `CF-Access-Client-Id:` or JSON/header syntax |
| `CLOUDFLARE_ACCESS_CLIENT_SECRET` | Same Cloudflare service token | Copy only the raw Client Secret value |
| `DEV_SSH_PRIVATE_KEY` | Private half of the dedicated GitHub deployment key | Entire OpenSSH private-key block including BEGIN/END lines and line breaks |
| `DEV_SSH_KNOWN_HOSTS` | Generated from the VM's trusted Hetzner console | Entire single `ssh-dev.yoursaynews.com ssh-ed25519 ...` line |
| `DEV_DB_JDBC_URL` | Aiven service host/port plus application DB | `jdbc:postgresql://HOST:PORT/your_say_news?sslmode=require` |
| `DEV_DB_REACTIVE_URL` | Same Aiven endpoint | `postgresql://HOST:PORT/your_say_news?sslmode=require` |
| `DEV_DB_MIGRATION_USERNAME` | Aiven service user | `ysn_migration` |
| `DEV_DB_MIGRATION_PASSWORD` | Aiven service user details | Password for `ysn_migration` |
| `DEV_DB_USERNAME` | Aiven service user | `ysn_runtime` |
| `DEV_DB_PASSWORD` | Aiven service user details | Password for `ysn_runtime` |
| `DEV_FIREBASE_ADMIN_CREDENTIALS_JSON` | Firebase Project settings -> Service accounts -> Firebase Admin SDK -> Generate new private key | The **whole downloaded JSON object**, unchanged; this is not `google-services.json` |
| `DEV_R2_ACCESS_KEY_ID` | R2 object-storage API token restricted to the media bucket | S3-compatible Access Key ID |
| `DEV_R2_SECRET_ACCESS_KEY` | Same R2 token | S3-compatible Secret Access Key |
| `DEV_GRAFANA_CLOUD_OTLP_ENDPOINT` | Grafana Cloud OpenTelemetry connection instructions | Complete OTLP endpoint expected by Alloy |
| `DEV_GRAFANA_CLOUD_OTLP_AUTHORIZATION` | Same Grafana instructions | Complete authorization header value, normally `Basic ...` |
| `DEV_OPENAI_API_KEY` | OpenAI project credential supplied by its owner | Currently a non-empty placeholder; replace with the real key |

The OpenAI placeholder satisfies the workflow's non-empty check and should allow the service to
start, but every AI feature will fail authentication until the owner replaces it. Do not describe
the placeholder as a working credential and do not enqueue AutoPost work until it is replaced.

GitHub never reveals saved secret values. Rotation means replacing the repository secret and, when
appropriate, revoking the old value at the provider.

## Hetzner VM and SSH

### What cloud-init created

Cloud-init:

- created the locked, unprivileged `deploy` user;
- copied every Hetzner-provisioned public key to both root and `deploy` authorized keys;
- disabled password and keyboard-interactive SSH;
- installed Docker Engine, Buildx, Compose and `cloudflared`;
- enabled Docker/containerd, unattended upgrades and UFW;
- denied public inbound traffic while allowing loopback and outbound traffic;
- created `/opt/your-say-news`; and
- installed `cloudflared-ysn.service`, initially unable to start until its token file exists.

### Add a new human SSH key

On the operator machine:

```bash
ssh-keygen -t ed25519 -a 100 -f ~/.ssh/id_ed25519_yoursaynews_dev
```

Upload only the `.pub` content in Hetzner Cloud -> the Your Say News project -> Security -> SSH
Keys. Never upload or share the private key.

Adding a key in Hetzner after the VM already exists does **not** automatically install it on that
VM. Use the Hetzner browser console as root and append the public key to
`/home/deploy/.ssh/authorized_keys`, then restore ownership and mode:

```bash
install -d -m 0700 -o deploy -g deploy /home/deploy/.ssh
editor /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 0600 /home/deploy/.ssh/authorized_keys
```

The two keys supplied at VM creation were named:

```text
TheoHutchings908-your-say-news-development
github-actions-your-say-news-development
```

The GitHub private-key secret must match the second key's public half. A human may use either an
independently authorized human key or another deliberately authorized key.

### Cloudflare Access application

In Cloudflare Zero Trust, the SSH Access application should protect the public DNS hostname
`ssh-dev.yoursaynews.com`:

1. Access controls -> Applications -> Add application.
2. Choose **Self-hosted and private**, then **Public DNS**.
3. Use the exact SSH hostname and create an Allow policy for authorized human identities.
4. Create a separate Service Auth policy that includes the dedicated CI service token.
5. Put that service token's raw ID and secret in the two GitHub secrets above.

Do not protect `dev.yoursaynews.com` with an interactive Access login page: the Android app needs
to reach the API directly and authenticates with Firebase instead.

### Install `cloudflared` locally

Use Cloudflare's official package for the operator OS and verify:

```bash
cloudflared --version
```

The successful connection shape used during setup was:

```bash
ssh \
  -i /home/theo/.ssh/id_ed25519_yoursaynews_dev \
  -o IdentitiesOnly=yes \
  -o 'ProxyCommand=/usr/local/bin/cloudflared access ssh --hostname %h' \
  deploy@ssh-dev.yoursaynews.com
```

If the binary is somewhere else, obtain it with `command -v cloudflared` and update the
`ProxyCommand` path. A reusable `~/.ssh/config` entry is:

```sshconfig
Host ysn-dev
  HostName ssh-dev.yoursaynews.com
  User deploy
  IdentityFile ~/.ssh/id_ed25519_yoursaynews_dev
  IdentitiesOnly yes
  ProxyCommand /usr/local/bin/cloudflared access ssh --hostname %h
```

Then connect with:

```bash
ssh ysn-dev
```

Human access may open a browser for Cloudflare authentication. `websocket: bad handshake` normally
means the SSH Access application/policy does not match the hostname or the identity is not allowed;
it is not an SSH-key error until the Cloudflare handshake succeeds.

### Break-glass Hetzner console and Tunnel bootstrap

Use the Hetzner browser console as root if the Tunnel is down. Verify cloud-init without exposing
any secret:

```bash
cloud-init status --wait
cat /var/lib/your-say-news/bootstrap-complete
test ! -e /var/lib/your-say-news/bootstrap-failed
```

The Tunnel token is the full `eyJ...` connector token obtained from the protected Terraform output
or Cloudflare Tunnel installation instructions. Put it in the environment file, not on the
systemd command line. Run each command separately in the web console; pasted multiline commands
were previously concatenated by the console.

```bash
install -o root -g cloudflared -m 0640 /dev/null /etc/cloudflared/tunnel.env
read -rsp 'Tunnel token: ' YSN_TUNNEL_BOOTSTRAP_TOKEN
printf '\n'
printf 'TUNNEL_TOKEN=%s\n' "$YSN_TUNNEL_BOOTSTRAP_TOKEN" > /etc/cloudflared/tunnel.env
unset YSN_TUNNEL_BOOTSTRAP_TOKEN
systemctl reset-failed cloudflared-ysn.service
systemctl restart cloudflared-ysn.service
systemctl status -l --no-pager cloudflared-ysn.service
journalctl -b -u cloudflared-ysn.service -n 100 --no-pager
```

The earlier `cloudflared tunnel run requires the ID or name` message meant the service started
without `TUNNEL_TOKEN`; it did not mean the token itself needed the Tunnel name appended.

### VM verification commands

As `deploy`:

```bash
cat /var/lib/your-say-news/bootstrap-complete
docker version
docker compose version
systemctl is-active cloudflared-ysn.service
systemctl is-active ssh.socket
systemctl is-active ssh.service
ss -lnt | grep ':22'
ls -ld /opt/your-say-news
```

The `deploy` account may see `-- No entries --` from `journalctl` because it is not in `adm` or
`systemd-journal`. Use `sudo` if deliberately granted, or use the trusted root console:

```bash
journalctl -b -u cloudflared-ysn.service -n 100 --no-pager
```

## Terraform operations

Terraform is pinned by the repository. Run static checks from the repository root:

```bash
terraform fmt -check -recursive -diff service/infra
cd service/infra/environments/development
terraform init -backend=false -input=false
terraform validate -no-color
terraform test -no-color
```

Normal changes are planned and applied through `.github/workflows/infra-dev.yml`:

1. Push the reviewed Terraform change.
2. Wait for the development Format, Validate/Test and Plan jobs.
3. Read the plan and reject unexpected deletion/replacement.
4. Copy the successful plan run ID and full 40-character commit SHA.
5. Manually run **Infrastructure Development** from that exact branch revision.
6. Enter the saved plan run ID, SHA and exact confirmation `apply development`.

A newer push invalidates the old approval context. Never apply an old plan after the branch moves.
Do not run a normal local `terraform apply` or retrieve sensitive outputs into shell history.

The Tunnel connector token is the exceptional one-time secret output. Retrieve it only through a
trusted protected Terraform UI/output path and deliver it through the Hetzner console procedure.

## Aiven PostgreSQL

The Aiven Quick Connect modal defaults to `avnadmin`; it does not necessarily provide a user
selector. For the application secrets, use the host and port from the service Overview, but replace
the user/password with the credentials on the service's **Users** page and replace `defaultdb` with
`your_say_news`.

Use `avnadmin` only for controlled administration. The deployed migration image uses
`ysn_migration`; `post-service` uses `ysn_runtime`.

Connection examples, with placeholders only:

```bash
psql 'postgresql://ysn_migration:PASSWORD@HOST:PORT/your_say_news?sslmode=require'
psql 'postgresql://ysn_runtime:PASSWORD@HOST:PORT/your_say_news?sslmode=require'
```

Useful non-secret verification after connecting as an administrator:

```sql
SELECT current_database(), current_user;
SELECT datname FROM pg_database WHERE datname = 'your_say_news';
SELECT usename FROM pg_user WHERE usename IN ('ysn_migration', 'ysn_runtime') ORDER BY usename;
SELECT has_database_privilege('ysn_migration', 'your_say_news', 'CONNECT') AS migration_connect,
       has_database_privilege('ysn_runtime', 'your_say_news', 'CONNECT') AS runtime_connect;
SELECT has_schema_privilege('ysn_migration', 'public', 'USAGE,CREATE') AS migration_schema,
       has_schema_privilege('ysn_runtime', 'public', 'USAGE') AS runtime_schema;
```

Successful `GRANT`/`ALTER DEFAULT PRIVILEGES` statements commonly return only command status and no
rows. Do not grant the runtime user schema ownership or migration privileges merely to make a test
pass.

## Firebase, Expo/EAS and Google Play

### Do not confuse the two Firebase JSON files

- `google-services.json` is Android **client** configuration consumed at native app build time.
- The Firebase Admin SDK service-account JSON is a privileged **server** credential consumed by
  `post-service` at runtime through `DEV_FIREBASE_ADMIN_CREDENTIALS_JSON`.

The Admin secret contains the whole JSON object downloaded from Firebase Project settings ->
Service accounts -> Firebase Admin SDK -> Generate new private key. The Node.js/Java radio buttons
only change the example snippet; the downloaded service-account JSON works with the Java backend.

The local Android client file is currently expected at:

```text
/home/theo/projects/your-say-news/google-services.json
```

It is ignored by the repository-wide `google-services*.json` rule and must never be committed.
Confirm before every commit:

```bash
git check-ignore -v /home/theo/projects/your-say-news/google-services.json
git status --short
```

### Current Expo linkage

Committed Expo identity:

```text
Owner:      your-say-news
Project:    your-say-news
Project ID: 4e62f40e-0837-4caf-9e26-e3d33e77e4ad
Package:    com.yoursaynews.app
API:        https://dev.yoursaynews.com/api
Channel:    development
```

The EAS project has a `GOOGLE_SERVICES_JSON` **File** environment variable with Secret visibility
for the `development` environment. A file variable is correct: EAS materializes it as a temporary
file and passes that path to `android.googleServicesFile`.

Secret EAS variables cannot be read during the CLI's initial local config evaluation. Therefore,
when starting a build locally, also provide the ignored local file path. The command that progressed
through archive upload and appeared in EAS Builds was:

```bash
cd /home/theo/projects/your-say-news/frontend/mobile/your-say-news

EXPO_PUBLIC_POST_SERVICE_HOST=http://localhost \
EXPO_PUBLIC_POST_SERVICE_PORT=8082 \
GOOGLE_SERVICES_JSON=/home/theo/projects/your-say-news/google-services.json \
bunx eas-cli@latest build \
  --platform android \
  --profile development-client
```

The local host/port values satisfy initial development config resolution; the
`development-client` profile then selects `APP_ENV=development` and embeds
`EXPO_PUBLIC_API_BASE_URL=https://dev.yoursaynews.com/api` from `eas.json`.

Verify the toolchain and link with:

```bash
node --version
bun --version
bunx eas-cli@latest whoami

EXPO_PUBLIC_POST_SERVICE_HOST=http://localhost \
EXPO_PUBLIC_POST_SERVICE_PORT=8082 \
bunx eas-cli@latest project:info
```

If `node` is missing, install an active Node release through the developer's NVM installation and
open a fresh shell. Bun remains the repository package manager, but Expo/EAS may spawn Node-based
CLI components.

Build profiles:

- `development-client`: internal APK with Expo development client; use for direct device testing.
- `development-store`: signed AAB for Google Play internal testing; increments Android versionCode.
- `production`: reserved and not ready for use.

Start a Play-ready AAB only after the backend snapshot deploys and the APK/auth flow is proven:

```bash
EXPO_PUBLIC_POST_SERVICE_HOST=http://localhost \
EXPO_PUBLIC_POST_SERVICE_PORT=8082 \
GOOGLE_SERVICES_JSON=/home/theo/projects/your-say-news/google-services.json \
bunx eas-cli@latest build \
  --platform android \
  --profile development-store
```

### Firebase/Play checks before distributing

In Firebase, confirm:

1. Authentication -> Sign-in method -> Google is enabled.
2. Project settings -> Android app is exactly `com.yoursaynews.app`.
3. The EAS upload/development keystore SHA-1 and SHA-256 are registered.
4. After Play App Signing is enabled, Play's **App signing key certificate** SHA-1 and SHA-256 are
   also registered. The EAS upload-key fingerprint alone is insufficient for a Play-installed app.
5. Download a fresh `google-services.json` after fingerprint/client changes and replace both the
   ignored local file and EAS File environment variable.

An EAS build being queued/running does not block backend infrastructure or backend deployment. It
does block installing/testing that new Android binary. Check the EAS Builds page for its final
result; the session ended without confirming it.

## Backend application snapshot and deployment

The workflow is `.github/workflows/dev-app.yml`. Relevant pushes execute:

1. `Test` — `:post-service:check` and the deployment shell/Compose contract.
2. `Build · Post-service image` — production Quarkus/Quinoa build, then JVM image tar archive.
3. `Build · Migration image` — Liquibase image tar archive.
4. `Publish · Commit snapshot` — loads both archives, pushes immutable GHCR snapshots and seals
   their digests into a one-day Actions artifact.
5. `Deploy · Explicit manual deployment` — runs only by manual dispatch with an approved snapshot.

The VM never checks out Git. The deployment job verifies the source workflow, exact commit,
checksums and image digests; renders `runtime.env` and the Firebase credential file; transfers a
release under `/opt/your-say-news/releases/<full-sha>`; runs forward-only Liquibase migrations;
starts `post-service` and Alloy; checks private and public health; then switches the `current`
symlink. A failed app release restores the prior containers but never reverses a database migration.

### Current CI blocker: Quinoa/Bun

Run `ff34e43` passed the application tests but failed `Build · Post-service image` before Docker
image construction:

```text
:post-service:quarkusAppPartsBuild FAILED
QuinoaProcessor#install
Error in Quinoa while running package manager ci command:
bun install --frozen-lockfile
```

What is proven:

- This is unrelated to the Node 20 and `punycode` deprecation warnings.
- It is unrelated to Docker Buildx; the Gradle/Quinoa assembly step occurs before Buildx in this
  composite action.
- `Test` passes because it uses `./gradlew :post-service:check`; that does not exercise the same
  production Quinoa bundle path as `quarkusBuild`.
- The normal CI workflow explicitly runs `oven-sh/setup-bun@v2`, but the application snapshot
  post-service build currently does not set up or pin Bun.
- Locally on this exact commit, Bun `1.3.14` successfully ran
  `CI=true bun install --frozen-lockfile` in `post-service/src/main/webui` with no lockfile change.
- Locally on this exact commit, Java 25 successfully ran
  `./gradlew :post-service:quarkusBuild --stacktrace --info`; Quinoa installed 84 packages, Vite
  built 69 modules, and Gradle completed successfully.
- Git remained clean after both local commands.

The GitHub log wraps Quinoa's exception and does not show Bun's underlying stderr, so the exact
remote cause is not yet proven. The strongest lead is an unpinned/mismatched runner Bun toolchain
or a transient registry/install failure, not a demonstrably stale `bun.lock`.

Recommended next change:

1. Add an explicit Bun setup step before `Build the post-service image` in the
   `build-post-service-image` job (or before Gradle in the composite action).
2. Pin `bun-version: 1.3.14`, matching the locally proven version.
3. Before retrying Gradle, temporarily print `bun --version` and run the exact install directly so
   a future failure exposes Bun's real stderr:

```yaml
- name: Set up Bun
  uses: oven-sh/setup-bun@v2
  with:
    bun-version: 1.3.14

- name: Verify admin frontend dependencies
  working-directory: post-service/src/main/webui
  shell: bash
  run: |
    bun --version
    bun install --frozen-lockfile
```

Keep or remove the explicit dependency preflight after diagnosis; keeping it makes the failure
clearer but duplicates Quinoa's install. Do not regenerate or loosen the lockfile until a direct CI
command proves the lockfile is the issue.

The preceding migration-image failure was already fixed in commit `ff34e43` by adding
`docker/setup-buildx-action` with `driver: docker-container` to both archive builders. A new commit
and new push workflow are required after the Bun fix; rerunning an older workflow does not include
new workflow code.

### Deploy after the snapshot becomes green

Wait until all four push jobs are green, especially `Publish · Commit snapshot`. Then:

1. Open that successful **Application Development** push run.
2. Copy its numeric run ID from the run URL/summary.
3. Copy its full 40-character commit SHA.
4. Choose **Run workflow** on `feat/add-infra` while that branch still resolves to the same SHA.
5. Enter the snapshot run ID, full SHA and exact confirmation:

```text
deploy development
```

Do not deploy from a run where only tests passed. The deployment requires the sealed artifact
created by `Publish · Commit snapshot`.

### Verify the first deployment

Publicly:

```bash
curl --fail --show-error https://dev.yoursaynews.com/api/live
```

On the VM as `deploy`:

```bash
readlink -f /opt/your-say-news/current
cd /opt/your-say-news/current
docker compose --env-file runtime.env ps
docker compose --env-file runtime.env logs --tail=100 post-service
docker compose --env-file runtime.env logs --tail=100 alloy
curl --fail --show-error http://127.0.0.1:8082/api/live
```

Never print `runtime.env`, `secrets/firebase-admin.json`, Docker credentials or the Tunnel env file.
The deploy workflow temporarily authenticates the VM to private GHCR and logs it out afterward.

## Remaining operational work

- Fix/pin Bun in the snapshot post-service build and obtain a completely green snapshot run.
- Confirm the migration-image job also passes with the new Buildx builder.
- Perform the first explicit backend deployment and save the workflow evidence.
- Confirm the earlier EAS development-client build result; install it and test Google sign-in plus
  authenticated calls to the deployed `/api` endpoint.
- Replace the OpenAI placeholder with the real owner-supplied key before testing AI features.
- Confirm Grafana receives traces, logs and metrics after deployment and configure alerts.
- Confirm R2 media upload/read using the restricted runtime credential.
- Configure/test database backups and record a restore test before claiming backups are usable.
- Add Play App Signing fingerprints to Firebase before distributing through Play.
- Build/submit `development-store` only after the development-client and backend are proven.
- Keep the environment synthetic-data-only until privacy, backup, alerting and operational gates
  in `plans/development-cloud-external-prerequisites.md` are complete.
- Do not create or apply production infrastructure from this branch.

## Detailed source documents

- `service/infra/README.md` — Terraform workflow and module ownership.
- `service/deploy/README.md` — snapshot/deployment/runtime contract.
- `docs/plans/development-cloud-infrastructure.md` — architecture and decisions.
- `docs/plans/development-cloud-external-prerequisites.md` — operational gates.
- `docs/plans/firebase-google-authentication-migration.md` — Firebase/EAS/Play delivery design.
- `wiki/ADR-035-2026-08-09-host-tunnel-bootstrap-boundary.md` — host-level Tunnel boundary.
- `wiki/ADR-036-2026-08-09-environment-api-url-shape.md` — `/api` URL contract.
- `wiki/ADR-037-2026-08-09-snapshot-release-image-channels.md` — snapshot release model.
