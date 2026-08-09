# Development cloud infrastructure plan

**Status:** Approved for implementation; external provisioning gates remain<br>
**Date:** 2026-07-25  
**Decision updates:** 2026-07-27 and 2026-08-09<br>
**Scope:** The always-on, production-like development environment only  
**Budget:** £20/month for compute, database, object storage and their network costs  
**Excluded from the budget:** AI API usage, the one-off Google Play registration fee and future
production support/SLA costs

**External setup checklist:**
[Development cloud external prerequisites](development-cloud-external-prerequisites.md)

## Contents

- [Recommendation](#recommendation)
- [Why this fits the repository](#why-this-fits-the-repository)
- [Wiki and roadmap reconciliation](#wiki-and-roadmap-reconciliation)
- [Target architecture](#target-architecture)
- [Expected monthly cost](#expected-monthly-cost)
  - [Cost assumptions and limits](#cost-assumptions-and-limits)
- [Does Akamai Cloud satisfy the project?](#does-akamai-cloud-satisfy-the-project)
- [Options considered](#options-considered)
- [Component design](#component-design)
  - [Compute and host baseline](#compute-and-host-baseline)
  - [PostgreSQL](#postgresql)
  - [Media and backups](#media-and-backups)
  - [Authentication, tester restriction and administration](#authentication-tester-restriction-and-administration)
  - [Availability and job integrity](#availability-and-job-integrity)
- [Observability](#observability)
- [Container images and deployment artifacts](#container-images-and-deployment-artifacts)
  - [Existing application image definition](#existing-application-image-definition)
- [Repository and Terraform design](#repository-and-terraform-design)
  - [Your Say News repository](#your-say-news-repository)
  - [Reusable modules and templates](#reusable-modules-and-templates)
  - [Terraform scope](#terraform-scope)
  - [Remote state](#remote-state)
- [CI/CD plan](#cicd-plan)
  - [Change classification](#change-classification)
  - [Pull requests](#pull-requests)
  - [Merge to main](#merge-to-main)
  - [API compatibility](#api-compatibility)
  - [Terraform plan and manual apply](#terraform-plan-and-manual-apply)
  - [Secrets and variables](#secrets-and-variables)
- [Application configuration work required](#application-configuration-work-required)
- [Migration to funded AWS](#migration-to-funded-aws)
- [Delivery phases and acceptance gates](#delivery-phases-and-acceptance-gates)
- [Decision record](#decision-record)
- [Primary references](#primary-references)

## Recommendation

Use a small EU Linux VM running Docker Compose, with state kept outside the VM:

- **Compute:** Hetzner Cloud CX23 in Nuremberg (`nbg1`) (2 shared vCPU, 4 GB RAM, 40 GB disk).
- **Database:** Aiven for PostgreSQL Free in its provider-assigned location for the synthetic-data
  proof of concept. Exact-region placement is not a Gate B requirement; reassess it before Gate D
  or funded production.
- **Media:** a private Cloudflare R2 Standard bucket created with the `eu` jurisdiction.
- **Ingress and DNS:** retain `yoursaynews.com` and `yoursaynews.co.uk` at GoDaddy, delegate both
  zones to Cloudflare, publish `dev.yoursaynews.com` through Cloudflare Tunnel and reserve
  `api.yoursaynews.com` for funded production. Redirect the `.co.uk` and `www` names to the `.com`
  apex, which initially serves a minimal coming-soon page.
- **Authentication:** Firebase Authentication with Google Sign-In; the application database
  remains authoritative for invitations and application permissions.
- **Observability:** Grafana Cloud Free, fed by OpenTelemetry/Alloy from the VM.
- **Images:** private GitHub Container Registry (GHCR), tagged by immutable Git commit SHA/digest.
- **Private operations and CI/CD:** use a separate Cloudflare Access policy and SSH hostname over
  the existing outbound-only Tunnel. Max and Theo authenticate separately; an ephemeral
  GitHub-hosted Ubuntu runner uses a narrowly scoped, rotating Access service token. Do not expose
  SSH publicly or install a GitHub runner on the application VM.
- **Infrastructure as code:** environment roots, reusable Terraform modules and Compose templates
  under the root-level `service/` directory in this repository, with remote state in HCP
  Terraform.
- **Orchestration:** Docker Compose now; AWS ECS is the funded migration target. Kubernetes and Helm
  are deliberately out of scope.

This is a production-shaped development environment, not a claim of production availability. It
has one API instance, one database node, no multi-zone failover and no paid support SLA. The design
keeps the application portable while accepting occasional development downtime.

## Why this fits the repository

The repository is not just one stateless HTTP container. The reviewed runtime comprises:

- a Quarkus/JVM `post-service` API;
- durable scheduled agent work that polls PostgreSQL and therefore needs an always-on process;
- one-shot Liquibase migrations;
- PostgreSQL accessed through both JDBC and reactive pools;
- direct client media upload/download via S3-compatible presigned URLs;
- xAI-compatible outbound calls;
- OpenTelemetry metrics, logs and traces; and
- an Expo/React Native Android application, built and distributed but not web-hosted.

The minimal `yoursaynews.com` coming-soon page is a separate static Cloudflare-hosted holding page,
not a web build of the Android application.

The current local Compose stack also contains Keycloak, a separate Keycloak database, LocalStack
and the local Grafana/OTel distribution. Those are local-development substitutes, not four more
cloud workloads:

- Google authentication replaces Keycloak and its database in the remote environment.
- R2 replaces LocalStack.
- Aiven replaces the local PostgreSQL container.
- Grafana Cloud replaces the local observability stack.

The API container, a lightweight telemetry collector and `cloudflared` are the only steady
Compose services proposed on the VM. Liquibase runs as a one-shot release step. The same
`cloudflared` connector carries the public API route and a separately Access-protected private SSH
route; the mobile API itself must not receive an Access browser-login challenge.

## Wiki and roadmap reconciliation

The complete `wiki/` ADR set and metric tracking records were reviewed. The infrastructure plan
preserves the following decisions:

- ADR-011 and ADR-012 require image/video media and presigned object URLs. Private R2 implements
  that contract without proxying media bytes through the JVM.
- ADR-020, ADR-022 and ADR-027 require durable asynchronous agent jobs. This is why a scale-to-zero
  platform is a poor fit while the worker polls every two seconds.
- ADR-023 makes the application database authoritative for account and publisher status. The
  accepted Google/Firebase identity change extends this principle to invitations and
  site-administrator permissions.
- ADR-024 and ADR-025 keep voting, users and agents in the single `post-service` deployable and
  central Liquibase tree. No microservices, Kubernetes cluster or separate worker container is
  introduced now.
- ADR-026 and every file under `wiki/all-metrics/` define the low-cardinality telemetry contract.
  Grafana Cloud should receive those metrics; infrastructure labels must not add user IDs, emails,
  post IDs or other high-cardinality/PII dimensions.
- ADR-027's fixed `ysn` application-owned publisher and audit requirements remain. Only its
  Keycloak `admin` role assumption needs superseding.

Historical accepted ADRs should not be rewritten. ADR-028,
`wiki/ADR-028-2026-07-25-google-authentication-and-application-authorization.md`, records the
authentication change and explicitly supersedes only the Keycloak-specific parts of ADR-023 and
ADR-027. Its application-owned authorisation direction is approved, with Firebase Authentication
selected as the managed token broker rather than an application-built issuer.

Two wiki risks must be closed before real people, rather than synthetic stakeholder data, use this
environment:

1. The database contains sensitive characteristic, disability, neurodiversity, income, vote and
   impression data. Complete the privacy, retention, account deletion/export and consent work
   already identified by the MVP plans.
2. Vote aggregation currently permits a suppression threshold of zero. The remote environment
   uses the approved threshold of `5`; the local MVP default must not leak into the tester
   deployment.

## Target architecture

```text
Android internal/closed testers
             |
       HTTPS + app token
             |
      Cloudflare DNS/WAF
             |
   Cloudflare Tunnel (outbound)
             |
  +----------v----------------------------------+
  | EU Linux VM                                 |
  | Docker Compose                              |
  |  - post-service JVM API                     |
  |  - cloudflared                              |
  |  - Grafana Alloy / OTel collector           |
  | Host: Docker, SSH, firewall, updates        |
  +------+------------------+-------------------+
         | TLS              | S3 HTTPS
         v                  v
  Aiven PostgreSQL      Private EU R2
  app data + jobs       images + videos

  Firebase/Google ------ identity assertion ----> API
  xAI API <------------- server-side egress ----- API
  Grafana Cloud <------- metrics/logs/traces ---- Alloy

  GitHub runner -- Cloudflare Access SSH -------> VM deploy user
  GitHub Actions -------> GHCR / HCP Terraform / EAS / Play
```

The native application necessarily contains its API hostname, so the URL cannot be treated as a
secret or made undiscoverable. Security comes from HTTPS, valid Google identity, the database
invitation/allowlist and server-side permissions. Cloudflare Access browser redirects should not
be placed in front of the mobile API. The administration endpoints receive the stricter
application-owned `ADMIN` permission.

## Expected monthly cost

Prices were checked on 2026-07-25. Currency conversion and VAT vary, so keep headroom rather than
budgeting to the penny.

| Component | Development selection | Estimated monthly cost |
| --- | --- | ---: |
| API VM | Hetzner CX23, EU, plus primary IPv4 if required | about €6–€8 including VAT |
| PostgreSQL | Aiven Free in its provider-assigned location | £0 |
| Media | R2 Standard, below 10 GB and free operation allowances | £0 |
| DNS, tunnel, Access, redirects, coming-soon page and TLS | Cloudflare Free | £0 |
| Telemetry | Grafana Cloud Free | £0 |
| Terraform state | HCP Terraform Free, below 500 managed resources | £0 |
| CI | GitHub-hosted Linux, within the repository owner's allowance | £0 |
| Image registry | GHCR, within allowance; control retention | £0 initially |
| Android build | EAS Free or GitHub Linux fallback | £0 initially |
| **Expected recurring total** | | **approximately £6–£8/month** |

Allow a **£5 operational contingency** for image/artifact overage, R2 operations or a paid Aiven
Developer plan. This still leaves the environment below £20 in the normal case. A region-fixed
Scaleway DB-DEV-S instead brings the estimated total close to £18–£20/month including tax and
small storage/backup volumes, so it has almost no contingency. Configure billing alerts at 50%,
75%, 90% and 100% wherever providers support them.

The Google Play personal developer account is a one-off US$25 charge and is not infrastructure
spend. Google documents both the fee and extra testing/device-verification requirements for new
personal accounts:
[Play Console registration](https://support.google.com/googleplay/android-developer/answer/6112435).

### Cost assumptions and limits

- Hetzner's June 2026 price table lists CX23 at €5.49/month before tax in its principal EU pricing
  column. Confirm stock, location, tax and IPv4 price at purchase:
  [Hetzner 2026 pricing adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/).
- Aiven Free is one node with 1 CPU, 1 GB RAM, 1 GB disk, backups and at most 20 connections. It has
  no SLA, VPC, static IP or connection pooling. Aiven assigns the Free service location and
  reserves the right to move its provider/region or stop an inactive Free service. That trade-off
  is accepted for synthetic proof-of-concept data:
  [Aiven Free PostgreSQL limitations](https://aiven.io/docs/products/postgresql/concepts/pg-free-tier).
- Scaleway's Paris DB-DEV-S is currently €0.0156/hour before tax plus block storage and backups,
  providing a deterministic EU fallback:
  [Scaleway managed database pricing](https://www.scaleway.com/en/pricing/managed-databases/).
- R2 Standard includes 10 GB-month, one million Class A operations and ten million Class B
  operations monthly, with no direct R2 egress charge:
  [R2 pricing](https://developers.cloudflare.com/r2/pricing/).
- HCP Terraform Free supports up to 500 managed resources and at least the last 100 states:
  [HCP Terraform limits](https://support.hashicorp.com/hc/en-us/articles/4414055267603-HCP-Terraform-Limits).
- Grafana Cloud Free currently includes 10,000 metric series and 50 GB each of logs, traces and
  profiles with 14-day retention:
  [Grafana Cloud Free](https://grafana.com/get/).
- GitHub Free currently includes 2,000 private-repository Actions minutes and 500 MB of shared
  Actions/package storage:
  [GitHub included usage](https://docs.github.com/en/billing/reference/product-usage-included).
- Tailscale Personal is not suitable because its free tier is restricted to non-commercial use.
  Standard is currently US$8 per user/month, so two paid seats would consume most of the
  environment budget:
  [Tailscale pricing](https://tailscale.com/pricing). Cloudflare Tunnel and Access instead provide
  the already-approved outbound connector, identity-gated SSH and CI service-token path without
  another provider or recurring charge:
  [Cloudflare SSH](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/use-cases/ssh/),
  [Cloudflare service tokens](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/).

Free tiers are dependencies, not permanent entitlements. Pin the date and limits in the monthly
cost review and alert before a provider changes them.

## Does Akamai Cloud satisfy the project?

**Yes technically, but not as the recommended full stack under this budget.**

Akamai offers London and continental European compute, Terraform support, managed PostgreSQL and
S3-compatible object storage. It is a credible and familiar alternative. Its current European
price page lists:

- 1 GB shared compute at US$5/month;
- 2 GB shared compute at US$12/month;
- 4 GB shared compute at US$24/month;
- one-node 1 GB managed PostgreSQL at US$16/month; and
- object storage at US$0.02/GB-month with the first 1 TB outbound transfer free.

Source: [Akamai Europe cloud pricing](https://www.akamai.com/cloud/pricing/europe).

The Quarkus JVM, collector and tunnel should not be deliberately squeezed into a 1 GB VM. The
smallest credible Akamai combination is therefore 2 GB compute plus 1 GB managed PostgreSQL at
about US$28/month before tax and storage. It is above £20 and has less API memory than the proposed
Hetzner VM.

Use Akamai in either of these situations:

- Hetzner capacity/account availability is a problem: run the 2 GB Akamai VM while retaining Aiven
  and R2, for about US$12/month plus tax.
- A single provider and London placement become more valuable than the £20 ceiling: use Akamai
  compute and managed PostgreSQL, retaining R2 only if its zero-egress media economics are useful.

Do not use Akamai's 1 GB VM for this JVM solely to save US$7. An out-of-memory loop is not a useful
production rehearsal.

## Options considered

| Option | Fit | Cost/operational conclusion |
| --- | --- | --- |
| **Hetzner VM + Aiven PG + R2** | Best current fit | Lowest predictable cost, 4 GB API memory and standard interfaces; accepts Aiven's provider-assigned Free location and limits for synthetic data. |
| **Akamai VM + Aiven PG + R2** | Best fallback | Familiar provider, London available, still below budget with 2 GB VM; less memory. |
| **Akamai VM + Akamai managed PG** | Technically strong | One provider and clean Terraform story, but credible minimum is above budget. |
| Hetzner VM + Scaleway managed PG + R2 | Region-fixed EU fallback | Paris/Milan placement and managed backups; approximately consumes the full £20 ceiling after tax and small storage volumes. |
| Scaleway compute + managed PG + R2 | Single-EU-provider alternative | Good EU control and standard services; recalculate VM/network prices before selection. |
| Supabase Free PG + R2 | Secondary DB option | Only about 500 MB database storage and inactivity constraints; application features are unnecessary. |
| Neon Free PG + R2 | Poor with current worker | Compute-hour economics conflict with an API worker polling every two seconds; revisit after event-driven jobs. |
| Fly.io / Render / Railway | Convenient PaaS | Less host work, but always-on JVM plus managed PG is generally less predictable or above this budget. |
| DigitalOcean | Simple mainstream option | A suitable 4 GB VM alone is around/above the budget; managed PG increases it further. |
| Self-host PostgreSQL on the VM | Cheapest paid resources | Rejected: API/database failure share one disk and VM, and two developers become the database operations team. |
| Kubernetes/Helm | Scalable eventually | Rejected now: unnecessary cost and control-plane/operations complexity for one deployable and under 100 users. |

Serverless/scale-to-zero container products were considered, but the durable worker currently polls
every two seconds and the requirement says the app is always available. A small VM gives a lower
and more predictable always-on cost. ECS remains the future scaling path.

## Component design

### Compute and host baseline

Provision Ubuntu 24.04 LTS x86-64 with:

- Docker Engine and the Compose v2 plugin from pinned repositories;
- automatic security updates with a Sunday 04:00 `Europe/London` maintenance/reboot window;
- an unprivileged `deploy` user, Docker access limited to that account and no password login;
- Cloudflare Access for identity-gated SSH/operations access over the outbound Tunnel;
- provider firewall and host firewall denying all public inbound ports;
- `cloudflared` making outbound-only connections to Cloudflare;
- disk, memory, JVM and container-restart alerts; and
- no persistent application data beyond container layers and bounded local log buffers.

Cloudflare documents that Tunnel is outbound-only and lets the firewall block all inbound traffic:
[Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/).

Set container memory reservations/limits after a load test, not by guesswork. A useful starting
budget on 4 GB is:

- API JVM: maximum heap around 1.5 GB, container limit around 2.5 GB;
- Alloy/collector: 256–384 MB;
- `cloudflared`: 128 MB;
- host, Docker and page cache: the remainder.

Measure startup, steady state and one concurrent agent-generation job before accepting the limits.
Stay on the JVM for this environment; native compilation is deferred because its build time and
maintenance cost do not currently earn enough infrastructure savings.

### PostgreSQL

Use managed PostgreSQL over TLS. Aiven chooses the Free service's cloud and region, and may change
them. This is accepted only while the environment contains synthetic proof-of-concept data. If an
exact EU location becomes mandatory before real testers or funded production, move to a
region-selectable paid Aiven plan or a fixed-region provider such as Scaleway.

Create separate least-privilege credentials for:

- the API runtime;
- Liquibase/schema migration; and
- read-only operational diagnostics if needed.

The API currently has JDBC and reactive pools. Aiven Free permits at most 20 connections and may
consume some itself, so cap the combined application pools conservatively (for example four JDBC
plus six reactive connections) and give migrations one short-lived connection. Load-test and tune
these values before deployment.

Enable Aiven termination protection in Terraform and `prevent_destroy` in the environment root.
The Aiven provider explicitly warns that some changes recreate stateful services:
[Aiven Terraform provider](https://registry.terraform.io/providers/aiven/aiven/latest/docs).

Create automated logical dumps to a separate private R2 backup bucket even though Aiven Free
includes backups. Encrypt them, use a dedicated write-only credential, retain daily backups for
seven days and monthly backups for three months, and test restore quarterly. Do not claim a backup
is usable until a restore drill succeeds.

Upgrade/re-platform triggers:

- database disk exceeds 60–70%;
- sustained memory/connection pressure;
- an exact EU location, static allowlist/VPC or formal SLA becomes mandatory;
- backups/restore objectives cannot be met; or
- real production users are admitted.

For the funded AWS target use RDS PostgreSQL. For a nearer-term paid EU fallback compare Scaleway
managed PostgreSQL, Aiven's region-selectable professional tiers and Akamai managed PostgreSQL at
the time the trigger fires. The US$5 Aiven Developer plan increases storage and uptime but still
does not permit an exact region, so it is a capacity upgrade rather than a stronger residency
option.

### Media and backups

Create two separate private R2 buckets in the `eu` jurisdiction:

- `...-media-development` for user/post images and videos;
- `...-backup-development` for encrypted database dumps.

The EU jurisdiction must be selected when the bucket is created and cannot later be changed.
Cloudflare documents the EU S3 endpoint and `region = "auto"`:
[R2 data location](https://developers.cloudflare.com/r2/reference/data-location/).

Keep the existing direct presigned URL pattern, with these production requirements:

- short expiry (the existing 15 minutes is reasonable);
- object keys generated by the server and scoped to the authorised publisher/post;
- private objects and expiring GET URLs;
- exact content-type allowlist, content-length limit and checksum;
- R2 CORS restricted to the expected application upload behaviour;
- per-user/account upload quotas and rate limits;
- lifecycle removal of abandoned uploads;
- audit metadata without PII in the object key; and
- malware/content moderation before media becomes visible to other testers.

The current client can hold a whole blob and supports video up to 200 MB. Before wider testing,
avoid buffering large videos in the JVM, add resumable/multipart upload if mobile reliability
requires it, and test on a slow connection.

R2's jurisdiction covers R2 object storage/processing, not a blanket guarantee that every
Cloudflare, Google, HCP or telemetry operation is EU-only. A future strict residency requirement
needs a processor-by-processor/DPA review and may require paid data-localisation products.

### Authentication, tester restriction and administration

Use Firebase Authentication with Google Sign-In as the managed identity/session broker, but do not
make Firebase or Google the application authorisation database:

1. The Android client signs in with Google through the supported Firebase/Credential Manager flow.
2. The backend verifies the Firebase ID-token signature, issuer, audience and expiry and applies
   revocation checks where the operation requires them.
3. Link accounts using the broker's immutable Firebase UID (`sub`), never mutable email. Preserve
   the external issuer/subject mapping needed for a future broker migration.
4. Check a database invitation/allowlist before creating or enabling an application account.
5. Load account activity, application permissions and publishing status from PostgreSQL on each
   protected operation so application revocation is not delayed by a still-valid identity token.

Firebase issues short-lived ID tokens and managed refresh sessions and supports server-side refresh
revocation. No application token issuer or signing-key service will be built:
[Firebase session management](https://firebase.google.com/docs/auth/admin/manage-sessions).

Firebase Authentication processes authentication data in US data centres. This is accepted under
the selected cost-first proof-of-concept standard: R2 uses its immutable EU jurisdiction, Aiven
assigns the Free database location and only synthetic data is permitted before Gate D. A future
strict all-processors-EU requirement must replace or renegotiate non-compliant processors before it
can become an acceptance criterion:
[Firebase privacy and data locations](https://firebase.google.com/support/privacy/).

Add application permissions such as `USER` and `ADMIN` independently from `AccountType` and
`PublisherStatus`. Bootstrap the first admin through a one-time migration or privileged CLI using
the immutable broker subject, not an email committed to Terraform. Every invite, revoke,
permission and publisher change must be audited.

Add an infrastructure-aware admin-page TODO:

- admin UI can be a later protected web/mobile surface;
- its endpoints use application `ADMIN`, not a Google or Keycloak admin role;
- no administration database or public VM port is added;
- Cloudflare may apply additional rate/WAF controls to `/admin/*`; and
- for the first release, a controlled audited CLI/migration is acceptable.

### Availability and job integrity

Compose should use restart policies, health checks and bounded log rotation. A single VM still has
a failure domain; an automatic container restart is not high availability.

`AgentJobWorker` skips overlapping scheduled executions and uses durable database claims. Before
adding a second API task in ECS, add a lease/heartbeat and recovery path for jobs left in an
in-progress state after process death. Then prove that multiple workers cannot publish the same
job. This is a prerequisite for horizontal scaling, not a reason to add a second container today.

### Observability

Send the metrics in `wiki/all-metrics/` plus structured logs and sampled traces to Grafana Cloud
Free. Use Grafana Alloy or an OpenTelemetry Collector on the VM:

- redact tokens, query parameters, user attributes, post text and AI prompts/responses;
- keep labels low-cardinality as required by ADR-026;
- sample successful traces and retain errors;
- bound local buffering so a Grafana outage cannot fill the VM disk;
- create alerts for API availability, 5xx rate, latency, JVM heap/GC, disk, container restart,
  database connections/storage, failed migrations, R2 failures and stuck/failed agent jobs; and
- add a synthetic health check that traverses Cloudflare to a readiness endpoint which verifies
  dependency health without exposing secrets.

The free tier has only three active users, which covers the current two developers.

## Container images and deployment artifacts

Use private GHCR packages for:

- `post-service` runtime image; and
- a matching Liquibase migration image or immutable migration artifact.

Build once and promote the same digest. Tag every image with `sha-<full-or-unambiguous-commit>`,
optionally add a release label, and deploy by digest. Never deploy `latest`.

Retain:

- the currently deployed API and migration artifacts;
- the immediately previous rollback set; and
- one known-good emergency set.

Delete untagged/intermediate packages after a short retention period. GitHub's 500 MB free storage
is shared across packages and Actions artifacts, so a large UBI/JVM image can exceed it. Set a
strict budget and measure the first image before assuming registry cost is zero.

The VM needs read-only package access. Prefer a dedicated machine/service identity with only
`read:packages`, stored in the root-owned Docker credential store and rotated; do not copy a
developer's broad token to the VM. GitHub Actions should push using its scoped `GITHUB_TOKEN`.

Generate an SBOM, scan the filesystem/image, sign the digest with keyless OIDC signing where
supported, and verify the expected digest in deployment. Pin base images by digest and renovate
them through reviewed pull requests.

### Existing application image definition

`post-service/src/main/docker/Dockerfile.jvm` remains the authoritative application image
definition. The infrastructure skeleton must not change its runtime base image, exposed port,
packaging layout or entrypoint. Build and validate that existing image as a separate application
concern before the first remote deployment.

Any future image hardening, base-image update, health-check metadata or port change requires its own
reviewed application change with an image build and runtime test. The deployment configuration
should consume the resulting immutable application image digest rather than silently redefining
the application image contract.

## Repository and Terraform design

Keep reusable infrastructure and deployment material beside its only current consumer. This lets
application, runtime-contract and infrastructure changes be reviewed and tested atomically without
introducing cross-repository release/version coordination for one application and two developers.
Do not put live environment state or environment-specific secrets in Git.

### Your Say News repository

Keep the backend and its service operations material in the existing Your Say News repository.
`post-service/` remains the actual backend module. A separate root-level `service/` directory owns
the infrastructure and deployment configuration through `service/infra/` and `service/deploy/`;
it is a directory in this repository, not another repository or backend service:

```text
your-say-news/                    # existing repository root
  post-service/                   # existing backend module
  service/                        # infrastructure and deployment operations
    infra/
      modules/
        linux-compose-host/
        cloudflare-api-tunnel/
        r2-private-bucket/
        aiven-postgresql/
      environments/
        development/
          backend.tf
          main.tf
          providers.tf
          variables.tf
          outputs.tf
          development.tfvars     # non-secret values only
      README.md
    deploy/
      compose.yaml               # service-owned Compose overlay/root
      env.example                # names and safe defaults, no values
      templates/
        single-jvm-api/
      scripts/
        deploy.ps1-or-sh         # thin, tested invocation
        health-check.ps1-or-sh
      README.md
```

`service/infra/environments` owns environment composition: provider choices, local module calls,
tfvars and outputs. `service/infra/modules` owns reusable provider-specific behaviour.
`service/deploy` identifies the exact runtime image, migration, environment contract and
health/rollback procedure, while `service/deploy/templates` holds reusable Compose behaviour when
it is genuinely shared. There is no `chart/` directory because there is no Kubernetes or Helm
deployment.

### Reusable modules and templates

```text
service/
  infra/modules/
    linux-compose-host/
    cloudflare-api-tunnel/
    r2-private-bucket/
    aiven-postgresql/
  deploy/templates/
    single-jvm-api/
```

Modules have narrow provider-specific responsibilities, explicit input/output contracts and
deletion protection. Environment roots reference them through repository-local paths, so the
reviewed application commit is also the immutable module/template version. Changes under
`service/infra/modules/**` and `service/deploy/templates/**` require a pull request and
the normal automated checks. Max (`Max-Hutchings`) and Theo (`TheoHutchings908`) are co-owners;
this trusted development environment does not require independent approval, and the author may
approve their own change or deployment.

Do not force abstraction into the first implementation: the development Compose root can remain
the reference implementation until behaviour is actually shared. If a second application
repository needs these modules/templates, extract the stable boundaries into a platform repository
with their Git history and begin pinning consumers to immutable release tags or commit SHAs.

Service environment changes still receive an automatic Terraform plan, but **every apply is
manual** so both developers can read the exact plan first.

### Terraform scope

Manage these resources in Terraform where the provider supports them safely:

- Hetzner VM, firewall, SSH key references, delete/rebuild protection;
- Aiven project/service/database users and termination protection;
- Cloudflare zone records, redirects, R2 buckets/CORS/lifecycle, Tunnel, Access policies and the
  private SSH application;
- Grafana Cloud stack/service-account configuration where useful;
- non-secret GitHub workflow configuration where safe. Private GitHub Free does not expose
  Environment secrets or approval rules, so it uses repository secrets plus explicit manual
  workflow dispatch.

Do not put these in Terraform state:

- Google user emails/invitations;
- application user/admin/publisher records;
- database passwords when a provider can generate and deliver them separately;
- xAI keys, Play credentials or private media; or
- rendered `.env` files.

Provider resources often expose generated credentials in state even when an output is marked
sensitive. Treat Terraform state as secret.

### Remote state

Use one HCP Terraform organisation and a workspace per environment, initially
`your-say-news-development`. Select an HCP Europe organisation if available to the account. Use
HCP for remote state, locking, history and team access, but keep execution in GitHub Actions so the
plan/apply approval path stays visible in the repository.

- Store a narrowly scoped HCP token in GitHub Actions repository secrets while this private
  repository remains on GitHub Free.
- Require MFA for both developers and remove unused tokens.
- Never use local state in CI or upload state as an Actions artifact.
- Save the binary plan in the same trusted workflow run that later applies it.
- Reject the apply if the commit, lock file, provider versions, local module sources or state serial
  changed.

When moving to AWS, migrate state deliberately to an encrypted versioned S3 backend with state
locking after the AWS account foundation exists. Do not copy state by hand.

## CI/CD plan

Keep the existing pull-request quality checks, but split the current monolithic `ci.yml` into
clear, path-aware workflows or jobs. Explicitly use `ubuntu-24.04`, not a mutable `ubuntu-latest`.
GitHub's standard private Linux runner currently provides 2 vCPU and 8 GB RAM:
[GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).

### Change classification

At the start of every pull request and `main` run, classify changes:

| Paths | Backend release | Android release | Terraform plan |
| --- | ---: | ---: | ---: |
| `post-service/**`, `liquibase/**` | yes | no | no |
| `frontend/**` | no | yes | no |
| shared API/schema/build config | yes | yes if client contract affected | as applicable |
| `service/infra/**` | no unless deploy contract changed | no | yes |
| `service/deploy/**` | yes | no | no |
| `docs/**`, `wiki/**` only | no | no | no |

Change detection optimises work; it must not bypass required checks. Define explicit shared paths
and test the filter itself.

### Pull requests

Backend job:

- set up Java 25 and Gradle caching;
- run formatting/static analysis, unit and integration tests;
- start real PostgreSQL for integration tests;
- run Liquibase from an empty schema and test upgrade from the prior released schema;
- build the JVM artifact and container;
- run dependency, secret and container vulnerability scans; and
- generate an SBOM.

Frontend job:

- set up pinned Bun and Node;
- install from lockfile;
- typecheck, lint and run Jest;
- run API contract/backward-compatibility tests; and
- build/typecheck the Android production configuration without submitting it.

Infrastructure job, when `service/infra/**` changes:

- `terraform fmt -check`, `init -backend=false`, `validate`;
- lock/provider/module checks;
- IaC security and policy checks;
- plan against the development workspace;
- publish the human-readable plan summary and machine-readable replacement/destruction check; and
- never apply from a pull request.

### Merge to `main`

Backend changes:

1. Re-run required backend checks.
2. Build API and migration artifacts once.
3. Generate SBOM, scan and sign.
4. Push immutable images to GHCR.
5. Enter the repository's explicit manual deployment workflow.
6. Authenticate to the dedicated Cloudflare SSH Access application with the scoped CI service
   token stored in GitHub Actions repository secrets.
7. SSH through `cloudflared` to the unprivileged deploy user; the VM exposes no public SSH port.
8. Pull the exact digest, run the one-shot Liquibase migration, then run Compose.
9. Check readiness through both localhost/private path and the public Cloudflare hostname.
10. Automatically roll the API back to the previous digest if health fails. Never automatically
    reverse a database migration.

The Access application has separate human allow rules for Max and Theo and a `Service Auth` rule
limited to the CI token. Set a rotation/expiry alert and revoke that token independently if the
deployment credential is exposed:
[Cloudflare service tokens](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/).

Frontend changes:

1. Re-run frontend checks.
2. Determine whether the native fingerprint changed.
3. Increment an Android version code from CI/release metadata.
4. Build an AAB through EAS Free; if its current quota is insufficient, use the GitHub Linux runner
   with the Android toolchain.
5. Submit with a Google Play service account to the internal testing track.
6. Promote to closed testing manually when desired.

Only frontend changes spend an Android build. There is no iOS job and no macOS runner. Register the
personal Google Play account and complete Google's personal-account testing/device-verification
requirements before expecting automated distribution to work.

If a commit changes both backend and frontend, release both. If it changes only one, release only
that component.

### API compatibility

The backend must support the current Android release and the immediately previous release. Enforce
this with:

- versioned/compatible API contracts;
- tolerant additions rather than removals/renames;
- expand-and-contract database/API migrations;
- CI tests using fixtures/contracts from both Android versions; and
- a minimum-supported-version response only for security emergencies.

A backend change that requires a client change must deploy the backward-compatible backend first,
publish the Android update, wait for adoption, then remove old behaviour in a later release.

### Terraform plan and manual apply

Any change under `service/infra/**` automatically creates and uploads a plan for the exact
commit. Applying is a separate, explicit `workflow_dispatch` run because private GitHub Free does
not provide protected Environments:

- require a manual approval/button after the plan is read; self-approval is permitted;
- apply only the saved binary plan, not a newly generated unreviewed plan;
- invalidate stale plans when main, state, provider locks or local module sources move;
- block any delete or replacement of PostgreSQL, R2, DNS zone or the VM unless a separate
  break-glass workflow and explicit second confirmation are used;
- serialize plans/applies with workflow concurrency; and
- publish apply outputs and audit links without secrets.

No independent reviewer is required for this trusted two-developer environment. The manual
`workflow_dispatch` apply requires the source plan run ID, exact commit SHA and environment-specific
confirmation phrase, with the same saved-plan checks.

### Secrets and variables

Use GitHub Actions repository secrets while this private repository remains on GitHub Free; never
use repository `.env` files. Reassess Environment secrets if the repository plan changes.

Non-secret environment variables include:

- environment name, region and hostname;
- R2 bucket name and non-secret endpoint;
- Google public client IDs/audiences;
- image repository/digest inputs;
- Grafana endpoints; and
- JVM/pool sizing.

Secrets include:

- provider/HCP tokens;
- database credentials;
- R2 credentials;
- xAI key;
- Grafana publishing token;
- Google/Play service-account material;
- Firebase administrative credential material where workload identity cannot replace it;
- Cloudflare Access CI service-token material;
- GHCR VM pull credential; and
- any temporary deployment credential not replaced by OIDC.

Use OIDC/workload federation wherever supported. Scope secrets to `development`, mask them, do not
pass them as Docker build arguments, and prevent forked pull requests from receiving them. Render
the VM runtime environment file at deploy time with owner-only permissions and atomically replace
it. In AWS, move runtime secrets to Secrets Manager and ECS task roles.

## Application configuration work required

The infrastructure cannot safely deploy the current local configuration unchanged.

### Backend

- Add a documented `%prod`/remote environment contract for database, Firebase/Google auth, R2, xAI
  and OTel.
- Remove Keycloak-only role assumptions after ADR approval.
- Replace localhost/static S3 endpoint and credentials with R2 endpoint/credential variables.
- Restrict CORS to actual mobile/API needs; remove local origins from the remote profile.
- Turn off Hibernate/Liquibase migrate-at-start when the release pipeline owns migration.
- Configure JDBC/reactive pools for the Aiven connection ceiling.
- Define readiness/liveness behaviour and do not expose sensitive config in health output.
- Set `votes.aggregation.suppress-below=5` in the remote environment before real tester
  demographics are used.
- Add job lease/recovery before more than one backend task is allowed.

### Frontend

- Replace localhost API host/port pieces with one HTTPS base URL.
- Replace Keycloak discovery/client configuration with Firebase Authentication and Google Sign-In.
- Set the permanent Android application/package ID to `com.yoursaynews.app`.
- Use `https://dev.yoursaynews.com` for the POC and reserve `https://api.yoursaynews.com` for the
  funded production build.
- Keep client IDs/public endpoints in build-time variables; never embed client secrets.
- Store refresh/session material only in platform secure storage.
- Add Android internal-release version and backend compatibility telemetry.
- Add upload size/type/checksum handling and graceful expired-presign retry.

### Liquibase

- Separate schema migrations from local seed/test identities.
- Make every released migration forward-only and backward-compatible with the prior application.
- Seed the application-owned `ysn` publisher idempotently without interactive credentials.
- Bootstrap initial admin identity through a controlled, audited secret/input outside Git.

## Migration to funded AWS

This design deliberately uses portable contracts:

| Development | Funded AWS target |
| --- | --- |
| Compose API container | ECS service/task (Fargate initially or EC2 capacity when cheaper) |
| One-shot migration container | ECS one-off task in the deployment workflow |
| Aiven PostgreSQL | RDS PostgreSQL |
| R2 S3-compatible API | S3 |
| GitHub Actions repository secrets | AWS Secrets Manager + task IAM role |
| Cloudflare Tunnel/DNS | ALB/API ingress; Cloudflare can stay or Route 53/CloudFront/WAF can replace it |
| Grafana Cloud | Keep Grafana Cloud or connect CloudWatch/managed Grafana |
| HCP Terraform state | Encrypted/versioned S3 backend with locking |
| Cloudflare Access SSH deploy path | GitHub OIDC to AWS plus ECS APIs/SSM; no SSH deployment |

The application image, environment-variable contract, PostgreSQL schema, S3 abstraction, Google
identity boundary and OpenTelemetry instrumentation remain. Migration replaces Terraform modules
and deployment adapters, not domain code.

Do not move to EKS. ECS provides service scheduling, rolling deployment, autoscaling and task
isolation without adding Kubernetes operations. Before the move:

- load-test and measure an ECS task size;
- make agent job leases safe for multiple replicas;
- introduce ALB health checks and minimum/maximum task counts;
- move runtime secrets to task roles/Secrets Manager;
- rehearse PostgreSQL dump/restore or logical replication into RDS;
- copy R2 objects to S3 and verify checksums;
- lower DNS TTL before cutover; and
- run old/new environments in parallel for a controlled acceptance window.

## Delivery phases and acceptance gates

### Phase 0 — approved decisions and account setup

- First, Theo creates the Hetzner Cloud account and development project, enables MFA/recovery and
  adds Max with separate recovery/co-owner access. Confirm Nuremberg stock, VAT-inclusive cost and
  the need for primary IPv4 before creating the VM.
- Review the approved plan and accepted ADR-028 managed-broker decision.
- Use Hetzner Nuremberg as primary and Akamai as fallback.
- Defer the Google Play personal account until Android distribution work begins.
- Delegate `yoursaynews.com` and `yoursaynews.co.uk` to Cloudflare, configure the approved
  redirects/hostnames and give Theo delegated GoDaddy access.
- Create provider accounts with MFA for both authorised developers.

**Gate:** the decisions and primary provider accounts are accepted. Provider identifiers,
credentials and Max-owned GoDaddy/xAI tasks remain before provisioning/deployment.

### Phase 1 — make the application deployable

- Build and validate the existing JVM Dockerfile without changing its application image contract.
- Add production environment contracts and Google authentication.
- Add application invitation/permission schema and audited admin bootstrap.
- Harden R2 uploads and separate migrations from application startup.
- Add job recovery and privacy configuration required by the wiki.

**Gate:** local production-profile Compose passes integration, security and rollback tests.

### Phase 2 — repository-local modules and Terraform

- Add path-specific CODEOWNERS naming both developers without requiring independent approval.
- Implement small provider-specific modules under `service/infra/modules` with deletion
  protection.
- Add reusable Compose behaviour under `service/deploy/templates` only where the concrete
  development deployment proves it is shared.
- Create the HCP organisation/workspace and GitHub Actions repository secrets.
- Add `service/infra/environments/development`.

**Gate:** automated plan is readable, contains no secret values and destructive changes are
blocked.

### Phase 3 — provision and deploy

- Provision VM, Aiven PG, R2 buckets, Cloudflare DNS/Tunnel/Access and Grafana stack.
- Bootstrap host with no public inbound ports.
- Build/sign/push images and deploy through the Access-protected SSH route.
- Run migration, seed `ysn`, bootstrap first admin and complete health checks.

**Gate:** API works through the app hostname, an uninvited Google account is denied, an invited
account works, R2 upload/read works, and the VM can be rebuilt without losing database/media.

### Phase 4 — Android and operations

- Build/submit only on frontend changes.
- Configure Play internal testers and then closed testing if required.
- Exercise API N/N-1 compatibility.
- Restore a PostgreSQL backup and verify R2 object checksums.
- Trigger alerts and rehearse API rollback.

**Gate:** both developers can operate/recover the environment from documented runbooks and monthly
spend remains below £20.

## Decision record

Decisions already made:

- Hetzner CX23 in Nuremberg as the primary one-node compute and Akamai 2 GB as fallback;
- Docker Compose, not Kubernetes/Helm;
- managed PostgreSQL separate from the API VM;
- Aiven Free in its provider-assigned location for synthetic proof-of-concept data, with an exact
  region reconsidered before Gate D or funded production;
- Cloudflare R2 EU media storage;
- `dev.yoursaynews.com` for the POC and `api.yoursaynews.com` reserved for funded production;
- a coming-soon page at `yoursaynews.com`, with `.co.uk` and `www` redirected to the `.com` apex;
- Firebase Authentication with Google Sign-In, not an application-built token issuer;
- application-owned invitations/roles;
- an initial vote-suppression threshold of `5`;
- synthetic data only until Gate D is complete;
- immutable EU R2 object storage; Aiven's provider-assigned Free database location is accepted only
  for synthetic proof-of-concept data;
- Cloudflare Access/Tunnel for human and CI SSH, with no Tailscale subscription;
- Max and Theo as co-owners with self-approval allowed;
- Theo as the operational DevOps owner, with Max retaining recovery/co-owner access;
- Sunday 04:00 `Europe/London` maintenance, alerts to both developers and alternating quarterly
  restore-test ownership;
- permanent Android package ID `com.yoursaynews.app`;
- Android-only testing through a personal Play account;
- automatic component-specific application releases;
- manual Terraform apply after automatic plan;
- free Grafana Cloud;
- HCP Terraform state;
- repository-local Terraform modules and deployment templates until a second repository needs
  them;
- future AWS ECS path; and
- AI API cost outside this budget.

No stakeholder architecture questions remain for the synthetic-data implementation. Outstanding
external work is collecting provider identifiers/credentials, completing Max's GoDaddy and xAI
tasks, and completing Gate D before real sensitive tester data is admitted.

## Primary references

- [Hetzner current price adjustment](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/)
- [Hetzner Terraform provider](https://registry.terraform.io/providers/hetznercloud/hcloud/latest/docs)
- [Aiven PostgreSQL Free](https://aiven.io/docs/products/postgresql/concepts/pg-free-tier)
- [Aiven service pricing and location limitations](https://aiven.io/docs/platform/concepts/service-pricing)
- [Aiven Terraform provider](https://registry.terraform.io/providers/aiven/aiven/latest/docs)
- [Scaleway managed database pricing](https://www.scaleway.com/en/pricing/managed-databases/)
- [Akamai Europe cloud pricing](https://www.akamai.com/cloud/pricing/europe)
- [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- [Cloudflare R2 EU jurisdiction](https://developers.cloudflare.com/r2/reference/data-location/)
- [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/)
- [Cloudflare SSH](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/use-cases/ssh/)
- [Cloudflare Access service tokens](https://developers.cloudflare.com/cloudflare-one/access-controls/service-credentials/service-tokens/)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
- [Firebase Authentication](https://firebase.google.com/docs/auth)
- [Firebase session management](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Firebase privacy and data locations](https://firebase.google.com/support/privacy/)
- [Google Play registration](https://support.google.com/googleplay/android-developer/answer/6112435)
- [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)
- [GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners)
- [Grafana Cloud Free](https://grafana.com/get/)
- [HCP Terraform plans](https://developer.hashicorp.com/terraform/cloud-docs/overview)
