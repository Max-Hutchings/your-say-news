# Development cloud external prerequisites

**Status:** Open checklist  
**Date:** 2026-07-26  
**Applies to:** The production-like development environment  
**Source plan:** [Development cloud infrastructure](development-cloud-infrastructure.md)  
**Authentication decision:** [ADR-028 — Google authentication and application-owned authorization](../../wiki/ADR-028-2026-07-25-google-authentication-and-application-authorization.md)

## Purpose

This checklist records everything that must be decided, created, obtained or verified outside the
codebase. It deliberately excludes implementation work.

Not every item blocks coding:

- **Gate A** blocks provider-specific configuration and authentication decisions.
- **Gate B** blocks a real Terraform plan and resource provisioning.
- **Gate C** blocks the first remote deployment.
- **Gate D** blocks admitting real testers or distributing through Google Play.

Do not commit credentials, Google subjects, tester emails, rendered environment files or other
personal/secret values to this document. Record secret material directly in the approved provider
or GitHub Environment secret store.

## Gate A — decisions needed before choices-dependent implementation

### Architecture and budget acceptance

- [ ] Both authorised developers accept the £20/month infrastructure ceiling.
- [ ] Both developers accept the expected normal cost of approximately £6–£8/month plus a £5
      operational contingency.
- [ ] Both developers accept a single VM, single database node, occasional development downtime
      and no paid SLA.
- [ ] Both developers accept Docker Compose rather than Kubernetes and AWS ECS as the eventual
      funded migration target.
- [ ] AI API usage and the one-off Google Play fee are accepted as outside the infrastructure
      budget.

Record:

```text
Decision owners:
Approval date:
Exceptions:
```

### Compute provider and location

- [ ] Confirm Hetzner CX23 as the primary compute choice.
- [ ] Confirm Akamai 2 GB compute with Aiven/R2 as the fallback if Hetzner account creation,
      capacity or availability fails.
- [ ] Choose Hetzner Nuremberg (`nbg1`) or Falkenstein (`fsn1`).
- [ ] At purchase time, confirm stock, VAT-inclusive price and the cost/necessity of primary IPv4.

Record:

```text
Primary provider:
Location:
Fallback:
IPv4 required:
Confirmed monthly price:
```

### PostgreSQL provider and residency

- [ ] Ask Aiven for written confirmation that selecting its **Europe** geographical area keeps
      both the database and provider backups in Europe.
- [ ] Store the written answer in the private environment evidence/runbook location.
- [ ] If Aiven cannot provide that confirmation, select Scaleway DB-DEV-S in Paris or Milan.
- [ ] Accept Aiven Free's 1 GB disk, maximum 20 connections, one node, no VPC/static IP/pooling and
      no SLA.
- [ ] Accept that Scaleway consumes most of the £20 budget if it becomes necessary.

Do not provision PostgreSQL until the residency question is resolved.

Record:

```text
Provider:
Plan:
Geographical area/region:
Residency evidence location:
Fallback decision:
```

### Domain and public hostname

- [ ] Confirm control of the existing GoDaddy registrar account.
- [ ] Choose the root DNS zone that Cloudflare will manage.
- [ ] Choose the exact development API hostname.
- [ ] Approve delegating the zone's nameservers to Cloudflare.
- [ ] Confirm the native application may contain and publicly reveal this hostname.
- [ ] Do not put Cloudflare Access browser redirects in front of the mobile API.

Record:

```text
Registered domain:
Cloudflare zone:
Development API hostname:
Registrar owner:
```

### Google authentication and application sessions

- [ ] Approve ADR-028 before removing Keycloak from the remote implementation.
- [ ] Confirm Google is the only external identity provider for this stage.
- [ ] Confirm PostgreSQL remains authoritative for invitations, account activity, `USER`/`ADMIN`,
      account type and publisher status.
- [ ] Choose one of:
  - [ ] application-issued, short-lived access and rotating refresh sessions; or
  - [ ] a reviewed managed Google-compatible token broker.
- [ ] If application sessions are chosen, appoint a security reviewer before implementation.
- [ ] Agree the signing/key-rotation and public-key-discovery design.
- [ ] Agree access and refresh lifetimes.
- [ ] Agree refresh rotation, reuse detection and revocation behavior.
- [ ] Agree logout and lost-device behavior.
- [ ] Agree audience separation between local and remote environments.
- [ ] Agree nonce, PKCE, CSRF and replay protections.
- [ ] Confirm secure Android storage requirements.

Do not implement an improvised token issuer. If the security requirements cannot be met, use a
reviewed managed broker while keeping application-owned invitations and permissions.

Record:

```text
ADR approved:
Session approach:
Security reviewer:
Design evidence location:
```

### Repository and approval ownership

- [ ] Record both developers' GitHub usernames.
- [ ] Identify the lead reviewer for infrastructure/module/template changes.
- [ ] Confirm who may approve application deployments.
- [ ] Confirm who may approve infrastructure applies.
- [ ] Confirm the GitHub repository plan supports protected Environment reviewers.
- [ ] Check whether the person who triggered a deployment can be prevented from approving it.
- [ ] If self-approval cannot be prevented, require the other developer's PR review as the
      compensating control.
- [ ] Provide SSH public keys or Tailscale identities for both operators.

Record:

```text
Developer 1 GitHub username:
Developer 2 GitHub username:
Lead infrastructure reviewer:
Deployment reviewers:
Infrastructure apply reviewers:
```

### Data, privacy and operational policy

- [ ] Confirm the environment remains synthetic-data-only until Gate D is complete.
- [ ] Choose and approve a non-zero vote-suppression threshold. The deployment skeleton currently
      uses `5` only as a provisional value.
- [ ] Decide whether EU database/object storage is sufficient or strict processor-by-processor
      EU-only handling is required.
- [ ] Accept or review the processors used by the plan: Hetzner/Akamai, Aiven/Scaleway,
      Cloudflare, Google, GitHub, HCP Terraform, Tailscale, Grafana, xAI, Expo and Google Play.
- [ ] Choose an automatic security-update maintenance/reboot window.
- [ ] Choose operational alert recipients.
- [ ] Assign ownership of quarterly database-restore tests.

Record:

```text
Synthetic-data-only:
Vote suppression threshold:
Residency standard:
Maintenance window and timezone:
Alert recipients:
Restore-test owner:
```

## Gate B — accounts and information needed before Terraform provisioning

### Account setup

Create organisation/team accounts where the provider supports them. Enable MFA for both authorised
developers, configure recovery access and remove unused personal tokens.

- [ ] Hetzner Cloud
- [ ] Aiven, or Scaleway after the residency decision
- [ ] Cloudflare
- [ ] Grafana Cloud
- [ ] HCP Terraform
- [ ] Tailscale
- [ ] Google Cloud
- [ ] GitHub/GHCR access confirmed
- [ ] xAI
- [ ] GoDaddy account access confirmed

Fallback accounts are not required unless their fallback condition is triggered. No AWS account is
required for this environment.

### Provider eligibility and plan checks

- [ ] Confirm Tailscale Personal is suitable for the project's current company/commercial use.
- [ ] Confirm the GitHub plan's Actions, package-storage and protected Environment limits.
- [ ] Confirm HCP Terraform can place the organisation/workspace in Europe if offered to the
      account.
- [ ] Confirm Grafana Cloud Free supports both developers and the intended telemetry volume.
- [ ] Confirm Cloudflare R2 can create both buckets in the `eu` jurisdiction.
- [ ] Configure billing alerts at 50%, 75%, 90% and 100% wherever supported.
- [ ] Add payment methods only through provider billing interfaces.

### Non-secret Terraform inputs

Collect and record these in the approved environment inventory. These values may later become
non-secret Terraform variables:

#### Hetzner

- [ ] Project/account identifier
- [ ] Approved location
- [ ] CX23 server type confirmation
- [ ] SSH public-key references
- [ ] Whether primary IPv4 is required

#### Aiven or Scaleway

- [ ] Organisation/project identifier
- [ ] Service name
- [ ] Plan
- [ ] Europe geographical area or exact region
- [ ] Termination-protection support

#### Cloudflare

- [ ] Account ID
- [ ] Zone ID and zone name
- [ ] Development API hostname
- [ ] Tunnel name
- [ ] Media bucket name
- [ ] Database-backup bucket name
- [ ] Confirmation that both buckets use the immutable `eu` jurisdiction

Suggested naming shape:

```text
<project>-media-development
<project>-backup-development
```

#### Grafana Cloud

- [ ] Stack name/ID
- [ ] Stack region
- [ ] OTLP endpoint
- [ ] Non-secret metrics/logs/traces endpoints needed by Alloy

#### HCP Terraform

- [ ] Organisation name
- [ ] Workspace `your-say-news-development`
- [ ] Execution remains in GitHub Actions, with HCP used for state, locking and history

#### Tailscale

- [ ] Tailnet/organisation
- [ ] VM tag, such as `tag:server`
- [ ] CI tag, such as `tag:ci`
- [ ] ACL ownership
- [ ] GitHub OIDC/workload-identity configuration

#### GitHub

- [ ] Repository owner
- [ ] `development` deployment Environment
- [ ] `development-infrastructure` apply Environment
- [ ] Required reviewers for both Environments
- [ ] GHCR package names for the API and migration images

#### Google Cloud

- [ ] Google Cloud project
- [ ] Android application/package ID
- [ ] Local Android client ID/audience
- [ ] Remote Android client ID/audience
- [ ] Backend/server client ID/audience where required by the chosen flow
- [ ] OAuth consent-screen ownership

## Gate C — credentials and operations needed before first deployment

### Secret inventory

Create secrets directly in provider stores or protected GitHub Environments. Do not paste their
values into issues, documentation, Terraform tfvars, chat or Docker build arguments.

| Secret | Required scope/location |
| --- | --- |
| Hetzner token | Narrow infrastructure provisioning scope |
| Aiven/Scaleway token | Development project/service scope |
| Cloudflare token | Only required zone, DNS, Tunnel and R2 permissions |
| HCP Terraform token | Development workspace/state scope |
| Grafana publishing token | Telemetry write only |
| PostgreSQL API credential | Runtime data access only |
| PostgreSQL migration credential | Schema migration only |
| PostgreSQL diagnostics credential | Read only, if required |
| R2 media credential | Development media bucket only |
| R2 backup credential | Write-only backup bucket access where possible |
| Cloudflare Tunnel token | Development tunnel only |
| Tailscale workload identity | Ephemeral `tag:ci` deployment access |
| GHCR pull credential | Dedicated machine identity with `read:packages` only |
| xAI key | Server-side development use with spend/rate controls |
| Google session/signing material | According to the approved security design |

### Deployment identities and access

- [ ] Create an unprivileged VM `deploy` user.
- [ ] Disable password login.
- [ ] Restrict Docker access to the deployment account.
- [ ] Route SSH/operations access over Tailscale.
- [ ] Deny all public inbound ports at provider and host firewalls.
- [ ] Create a dedicated GHCR machine/service identity; do not reuse a developer's broad token.
- [ ] Store the VM's Docker credential material in a root-owned location and define rotation.
- [ ] Confirm the Cloudflare Tunnel route points to `post-service:8082`.

### PostgreSQL access and backup operations

- [ ] Create separate least-privilege runtime and migration credentials.
- [ ] Create read-only diagnostics credentials only if operationally necessary.
- [ ] Cap the combined JDBC/reactive connection pools for the selected database limit.
- [ ] Create the separate private R2 backup bucket.
- [ ] Choose where the database-backup encryption key is held and who can recover it.
- [ ] Configure daily backups retained for seven days.
- [ ] Configure monthly backups retained for three months.
- [ ] Schedule quarterly restore tests.
- [ ] Record the first successful restore evidence before claiming backups are usable.

### Observability and operations

- [ ] Create Grafana alert destinations.
- [ ] Configure alerts for public API availability, 5xx rate, latency, JVM heap/GC, VM disk,
      container restarts, database pressure/storage, failed migrations, R2 failures and failed or
      stuck agent jobs.
- [ ] Configure a synthetic readiness check through Cloudflare.
- [ ] Agree trace sampling and PII/token/prompt redaction.
- [ ] Ensure local telemetry buffering cannot fill the VM disk.
- [ ] Agree container log-retention limits.

### First administrator and invitations

- [ ] Define the controlled invitation process.
- [ ] Define the first-admin bootstrap procedure and two-person recovery runbook.
- [ ] Obtain the initial administrator's immutable Google `sub` after the approved authentication
      flow is available.
- [ ] Supply the `sub` only through the audited migration/operational input.
- [ ] Never store Google subjects, emails, access tokens or refresh tokens in Git or Terraform
      state.
- [ ] Define where invitation, revocation, permission and publisher audit evidence is retained.

### Release operations

- [ ] Create GHCR packages for the runtime and migration images.
- [ ] Set a GHCR/Actions storage budget and retention policy.
- [ ] Retain the current, immediately previous and known-good emergency image sets.
- [ ] Confirm image signing and SBOM retention.
- [ ] Define the API rollback owner and procedure.
- [ ] Confirm database migrations are forward-only and are never automatically reversed.
- [ ] Confirm who manually approves Terraform apply after reading the saved plan.

## Gate D — requirements before admitting real testers

Until this gate is complete, use synthetic data only.

### Privacy and data governance

- [ ] Approve and publish the privacy policy.
- [ ] Approve consent wording and consent evidence retention.
- [ ] Define personal-data retention periods.
- [ ] Implement and operationally support account deletion.
- [ ] Implement and operationally support data export.
- [ ] Review processor terms/DPAs for the accepted residency standard.
- [ ] Confirm the final non-zero vote-suppression threshold.
- [ ] Ensure logs, metrics and traces exclude tokens, emails, Google subjects, user attributes,
      post text and AI prompts/responses.

### Media safety

- [ ] Approve exact image/video content types.
- [ ] Approve upload-size limits and account quotas.
- [ ] Approve checksum requirements.
- [ ] Define abandoned-upload lifecycle deletion.
- [ ] Select or define malware/content moderation before media becomes visible.
- [ ] Confirm object keys contain no PII.
- [ ] Test upload behavior on a slow/mobile connection.
- [ ] Decide whether resumable/multipart upload is required before wider testing.

### Google consent and tester administration

- [ ] Complete the Google OAuth consent screen.
- [ ] Provide the required privacy-policy URL.
- [ ] Register separate local and remote Google client IDs/audiences.
- [ ] Define the authorised tester/invitation list outside Git.
- [ ] Define lost-device, logout, session revocation and account-recovery support.

### Google Play and Android distribution

Start this work early because verification/testing requirements can introduce calendar delays.

- [ ] Register the Google Play personal developer account and pay the one-off fee.
- [ ] Complete Google's current identity and device-verification requirements.
- [ ] Complete the current testing requirements for a new personal developer account.
- [ ] Create the Play application.
- [ ] Confirm the final Android package/application ID.
- [ ] Configure Play App Signing and protect signing ownership/recovery.
- [ ] Create the internal tester list.
- [ ] Create the closed tester list when promotion is desired.
- [ ] Create a narrowly scoped Play submission service account.
- [ ] Create/configure the Expo/EAS account and project if EAS is used.
- [ ] Decide whether GitHub's Android toolchain is the fallback when EAS quota is insufficient.

### Operational readiness

- [ ] Both developers can deploy and roll back the API.
- [ ] Both developers can operate the first-admin recovery procedure.
- [ ] Complete and record a PostgreSQL restore drill.
- [ ] Verify R2 object checksums.
- [ ] Trigger and verify every critical alert.
- [ ] Rehearse API rollback without reversing a database migration.
- [ ] Confirm monthly spend remains below £20.
- [ ] Confirm the environment still supports the current and immediately previous Android release.

## Items not required for this development environment

- An AWS account or AWS production foundation
- Kubernetes, Helm or EKS
- iOS/macOS build infrastructure
- Akamai unless Hetzner is unavailable
- Scaleway unless Aiven cannot satisfy the residency requirement
- Paid production support or a formal production SLA
- A separate platform/deployment repository while this repository is the only consumer

## Minimum information needed to continue

The following answers unblock the next choices-dependent implementation work:

1. Hetzner Nuremberg or Falkenstein.
2. The intended domain and API hostname.
3. Whether Aiven Free is accepted if written Europe residency is confirmed.
4. Whether ADR-028 is approved and which session/broker approach is selected.
5. The security reviewer for application sessions, if selected.
6. Both developers' GitHub usernames and the lead infrastructure reviewer.
7. Confirmation that the environment is synthetic-data-only initially.
8. The provisional non-zero vote-suppression threshold.
9. Acceptance of the £20 budget, single-node availability and listed processors.

