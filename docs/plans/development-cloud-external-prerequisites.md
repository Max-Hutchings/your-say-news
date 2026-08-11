# Development cloud external prerequisites

**Status:** Gate A decisions recorded; provider setup remains open<br>
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

Do not commit credentials, Google/Firebase subjects, tester emails, rendered environment files or
other personal/secret values to this document. Record secret material directly in the approved
provider or approved GitHub Actions secret store.

## Gate A — decisions needed before choices-dependent implementation

### Architecture and budget acceptance

- [x] Both authorised developers accept the £20/month infrastructure ceiling.
- [x] Both developers accept the expected normal cost of approximately £6–£8/month plus a £5
      operational contingency.
- [x] Both developers accept a single VM, single database node, occasional development downtime
      and no paid SLA.
- [x] Both developers accept Docker Compose rather than Kubernetes and AWS ECS as the eventual
      funded migration target.
- [x] AI API usage and the one-off Google Play fee are accepted as outside the infrastructure
      budget.

Record:

```text
Decision owners: Max Hutchings and Theo Hutchings
Approval date: 2026-07-27
Exceptions: None
```

### Compute provider and location

- [x] Confirm Hetzner CX23 as the primary compute choice.
- [x] Confirm Akamai 2 GB compute with Aiven/R2 as the fallback if Hetzner account creation,
      capacity or availability fails.
- [x] Choose Hetzner Nuremberg (`nbg1`) or Falkenstein (`fsn1`).
- [ ] At purchase time, confirm stock, VAT-inclusive price and the cost/necessity of primary IPv4.

Record:

```text
Primary provider: Hetzner Cloud CX23
Location: Nuremberg (`nbg1`)
Fallback: Akamai 2 GB compute with Aiven/R2
IPv4 required:
Confirmed monthly price:
```

### PostgreSQL provider and residency

- [x] Accept Aiven Free's provider-assigned location for the synthetic-data proof of concept.
- [x] Accept that Aiven may change the Free service's cloud, region or configuration.
- [x] Defer any exact-region requirement until Gate D or a funded production environment.
- [x] Accept Aiven Free's 1 GB disk, maximum 20 connections, one node, no VPC/static IP/pooling and
      no SLA.
- [x] Retain Scaleway DB-DEV-S as a paid fallback only if Aiven Free becomes unavailable or no
      longer meets the proof-of-concept limits.

Record:

```text
Provider: Aiven
Plan: Aiven Free; fallback DB-DEV-S
Geographical area/region: Provider-assigned on Aiven Free
Residency decision: Exact database region is not a synthetic-data proof-of-concept gate
Fallback decision: Use Scaleway only if Aiven availability or capacity requires it
```

### Domain and public hostname

- [ ] Confirm operational access to the existing GoDaddy registrar account: Max owns the account
      and must grant Theo GoDaddy delegate access without sharing credentials.
- [x] Choose the root DNS zones that Cloudflare will manage.
- [x] Choose the exact development API hostname.
- [x] Approve delegating both zones' nameservers to Cloudflare.
- [x] Confirm the native application may contain and publicly reveal this hostname.
- [x] Do not put Cloudflare Access browser redirects in front of the mobile API.
- [x] Use the `/api` boundary on each environment origin: `dev.yoursaynews.com/api` for development
      and `yoursaynews.com/api` for future production.
- [x] Do not create a marketing or coming-soon frontend for the development hostname.
- [x] Leave the production apex frontend and API routing unprovisioned until production work begins.

Record:

```text
Registered domains: yoursaynews.com and yoursaynews.co.uk
Cloudflare zones: yoursaynews.com and yoursaynews.co.uk
Development API base URL: https://dev.yoursaynews.com/api
Future production API base URL: https://yoursaynews.com/api
Development web frontend: none
Future production web frontend: https://yoursaynews.com/
Registrar owner: Max Hutchings; Theo Hutchings to receive delegated domain-management access
Cloudflare account control: Max and Theo use separate MFA-protected members; Theo is operational owner
```

### Google authentication and application sessions

- [x] Approve ADR-028 before removing Keycloak from the remote implementation.
- [x] Confirm Google is the only external identity provider for this stage.
- [x] Confirm PostgreSQL remains authoritative for invitations, account activity, `USER`/`ADMIN`,
      account type and publisher status.
- [x] Choose Firebase Authentication as the managed Google-compatible token broker.
      Application-issued access/refresh sessions are explicitly not selected.
- [x] Confirm a custom-issuer security reviewer is not required because Firebase owns token
      issuance and signing-key rotation.
- [ ] Validate Firebase token verification, refresh/revocation, logout and lost-device behavior
      against the implementation.
- [ ] Configure distinct local/development/future-production Firebase projects or audiences so
      tokens cannot cross environments.
- [ ] Validate Credential Manager/Firebase replay protections and native Android token storage.

Do not implement an improvised token issuer. If the security requirements cannot be met, use a
reviewed managed broker while keeping application-owned invitations and permissions.

Record:

```text
ADR approved: Yes
Session approach: Firebase Authentication with Google Sign-In; PostgreSQL remains authoritative
Security reviewer: Not required for a custom issuer because no custom issuer will be built
Design evidence location: ADR-028 and the reviewed implementation plan
```

### Repository and approval ownership

- [x] Record both developers' GitHub usernames.
- [x] Confirm infrastructure/module/template ownership is shared; there is no mandatory lead
      reviewer.
- [x] Confirm either developer may approve their own application deployment.
- [x] Confirm either developer may approve their own infrastructure apply after reading the saved
      plan.
- [x] Confirm independent protected-Environment review is not required for this trusted
      two-developer environment.
- [ ] Configure separate Cloudflare Access identities for both operators and retain individual SSH
      public keys only for tested break-glass recovery.

Record:

```text
Developer 1 GitHub username: Max-Hutchings
Developer 2 GitHub username: TheoHutchings908
Lead infrastructure reviewer: None; Max and Theo are co-owners
Operational DevOps owner: Theo; Max retains recovery/co-owner access
Deployment reviewers: Initiating developer; self-approval allowed
Infrastructure apply reviewers: Initiating developer; self-approval allowed
```

### Data, privacy and operational policy

- [x] Confirm the environment remains synthetic-data-only until Gate D is complete.
- [x] Choose and approve a non-zero vote-suppression threshold: `5`.
- [x] Decide whether EU database/object storage is sufficient or strict processor-by-processor
      EU-only handling is required.
- [x] Accept or review the processors used by the plan: Hetzner/Akamai, Aiven/Scaleway,
      Cloudflare, Google/Firebase, GitHub, HCP Terraform, Grafana, xAI, Expo and Google Play.
- [x] Choose an automatic security-update maintenance/reboot window.
- [x] Choose operational alert recipients.
- [x] Assign ownership of quarterly database-restore tests.

Record:

```text
Synthetic-data-only: Yes, until Gate D is complete
Vote suppression threshold: 5
Residency standard: EU database and object storage; other processors accepted under reviewed terms
Maintenance window and timezone: Sunday 04:00 Europe/London
Alert recipients: Max Hutchings and Theo Hutchings
Restore-test owner: Alternates quarterly between Max and Theo
```

## Gate B — accounts and information needed before Terraform provisioning

### Account setup

Create organisation/team accounts where the provider supports them. Enable MFA for both authorised
developers, configure recovery access and remove unused personal tokens.

- [x] Hetzner Cloud
- [x] Aiven
- [x] Cloudflare
- [x] Grafana Cloud
- [x] HCP Terraform
- [x] Google Cloud
- [x] Firebase Authentication
- [x] GitHub/GHCR access confirmed
- [ ] xAI
- [ ] GoDaddy account access confirmed

Fallback accounts are not required unless their fallback condition is triggered. No AWS account is
required for this environment.

### Provider eligibility and plan checks

- [x] Confirm GitHub plan limits: this is a private repository on GitHub Free, so Environment
      secrets and protected Environment approval rules are unavailable. Use narrowly scoped
      repository secrets and the explicit `workflow_dispatch` apply confirmation.
- [x] Confirm HCP Terraform placement: the existing Free organisation is on global
      `app.terraform.io`; HCP Europe requires a separate account/contract, so the approved
      other-processors residency standard permits the global service for development state.
- [ ] Confirm Grafana Cloud Free supports both developers and the intended telemetry volume.
- [x] Activate the Cloudflare R2 subscription on the existing Cloudflare account.
- [ ] Confirm Cloudflare R2 can create both buckets in the `eu` jurisdiction.
- [x] Confirm Cloudflare's free plan supports the two-person proof-of-concept Access/Tunnel use.
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

- [x] Account ID: `9538d45e127bdb7d6b1bf1ecf9020146`
- [ ] Zone ID for the active `yoursaynews.com` zone; zone name is confirmed
- [x] Development API hostname: `dev.yoursaynews.com` with application-owned `/api` paths
- [x] Tunnel name: `your-say-news-development`
- [x] Private SSH hostname: `ssh-dev.yoursaynews.com`
- [ ] Confirm Universal SSL is active for `yoursaynews.com` and first-level subdomains.
- [ ] Enable Always Use HTTPS and retain a minimum TLS version of 1.2; leave HSTS disabled until
      every production hostname is deliberately live.
- [ ] Access application and policies for Max and Theo
- [ ] CI service-token identifier; Theo owns rotation and Max is backup
- [x] Media bucket name: `your-say-news-media-development`
- [x] Database-backup bucket name: `your-say-news-backup-development`
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

- [x] Organisation `your-say-news`
- [x] Workspace `your-say-news-development`
- [x] Execution mode is Local, with HCP used for state, locking and history and execution
      remaining in GitHub Actions

#### GitHub

- [x] Repository owner: `Max-Hutchings`
- [x] Deployment Environments are not used because private GitHub Free does not expose their
      secrets or approval rules
- [x] Provider credentials use narrowly scoped Actions repository secrets
- [x] Manual self-approval uses the explicit `workflow_dispatch` confirmation fallback from the
      approved infrastructure plan
- [x] GHCR snapshot package names: `your-say-news-post-service-snapshot` and
      `your-say-news-migrations-snapshot`; reserve the unsuffixed names for future tagged releases

#### Google Cloud

- [ ] Google Cloud project
- [x] Android application/package ID: `com.yoursaynews.app`
- [ ] Local Android client ID/audience
- [ ] Remote Android client ID/audience
- [ ] Backend/server client ID/audience where required by the chosen flow
- [ ] OAuth consent-screen ownership

## Gate C — credentials and operations needed before first deployment

### Secret inventory

Create secrets directly in provider stores or GitHub Actions repository secrets. Do not paste their
values into issues, documentation, Terraform tfvars, chat or Docker build arguments. Repository
secrets are the accepted fallback for this trusted two-developer private GitHub Free repository.

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
| Cloudflare Access CI service token | Development SSH Access application only; rotate and revoke independently |
| GHCR pull credential | Dedicated machine identity with `read:packages` only |
| xAI key | Server-side development use with spend/rate controls |
| Firebase administrative/broker material | According to the approved managed-broker design |

### Deployment identities and access

- [ ] Create an unprivileged VM `deploy` user.
- [ ] Disable password login.
- [ ] Restrict Docker access to the deployment account.
- [ ] Route SSH/operations access through a separate Cloudflare Access application over the
      outbound-only Tunnel; do not expose SSH publicly.
- [ ] Deny all public inbound ports at provider and host firewalls.
- [ ] Create a dedicated GHCR machine/service identity; do not reuse a developer's broad token.
- [ ] Store the VM's Docker credential material in a root-owned location and define rotation.
- [ ] Confirm the host-level Cloudflare Tunnel API route points to `http://localhost:8082`.

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
- [ ] Obtain the initial administrator's immutable Firebase UID/broker `sub` after the approved
      authentication flow is available.
- [ ] Supply the `sub` only through the audited migration/operational input.
- [ ] Never store Google/Firebase subjects, emails, access tokens or refresh tokens in Git or
      Terraform state.
- [ ] Define where invitation, revocation, permission and publisher audit evidence is retained.

### Release operations

- [ ] Publish the first commit so Actions creates the two private GHCR snapshot packages.
- [ ] Set a GHCR/Actions storage budget and retention policy.
- [ ] Retain the current, immediately previous and known-good emergency image sets.
- [ ] Confirm image signing and SBOM retention.
- [x] Define the API rollback owner: the deploying developer, with the other developer as backup;
      document and test the procedure before Gate D.
- [ ] Confirm database migrations are forward-only and are never automatically reversed.
- [x] Confirm the initiating developer manually approves Terraform apply after reading the saved
      plan; self-approval is allowed.

## Gate D — requirements before admitting real testers

Until this gate is complete, use synthetic data only.

### Privacy and data governance

- [ ] Approve and publish the privacy policy.
- [ ] Approve consent wording and consent evidence retention.
- [ ] Define personal-data retention periods.
- [ ] Implement and operationally support account deletion.
- [ ] Implement and operationally support data export.
- [ ] Review processor terms/DPAs for the accepted residency standard.
- [x] Confirm the final non-zero vote-suppression threshold: `5`.
- [ ] Ensure logs, metrics and traces exclude tokens, emails, Google/Firebase subjects, user attributes,
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
- [ ] Register separate local, development and future-production Firebase projects/audiences and
      Google client IDs.
- [ ] Define the authorised tester/invitation list outside Git.
- [ ] Define lost-device, logout, session revocation and account-recovery support.

### Google Play and Android distribution

Start this work early because verification/testing requirements can introduce calendar delays.

Implementation owner: **Max Hutchings**. Theo's responsibility is limited to running the reviewed
development infrastructure pipeline and granting Max Developer access to the shared Expo
Organization. Mobile delivery CI is deliberately deferred until the EAS project, Firebase Android
application and Google Play application have been linked and one manual build succeeds.

- [ ] Register the Google Play personal developer account and pay the one-off fee.
- [ ] Complete Google's current identity and device-verification requirements.
- [ ] Complete the current testing requirements for a new personal developer account.
- [ ] Create the Play application with testing title `Your Say News Dev`.
- [x] Confirm the final Android package/application ID: `com.yoursaynews.app`.
- [ ] Configure Play App Signing and protect signing ownership/recovery.
- [ ] Create the internal tester list.
- [ ] Create the closed tester list when promotion is desired.
- [ ] Create a narrowly scoped Play submission service account.
- [ ] Create a shared Expo Organization, invite Max with Developer access, and link the repository
      mobile app to its EAS project.
- [ ] Create mobile delivery CI only after Expo/EAS, Firebase Android and Play are linked and a
      manual signed build succeeds.
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
- Scaleway unless Aiven Free becomes unavailable or exceeds proof-of-concept limits
- Paid production support or a formal production SLA
- A separate platform/deployment repository while this repository is the only consumer

## Gate A outcome

All choices-dependent stakeholder questions needed to continue have been answered. Coding may
proceed against the recorded Nuremberg, domain, Firebase, privacy and ownership decisions.

The first action outside the codebase is:

1. Theo creates the Hetzner Cloud account and development project, enables MFA and recovery
   access, and adds Max with separate recovery/co-owner access. Do not create the VM until its
   Nuremberg stock, VAT-inclusive price and primary-IPv4 requirement have been checked.

Then complete the remaining external execution/evidence:

2. Record the exact Aiven project, Free plan and provider-assigned cloud identifiers used by
   Terraform.
3. Max grants Theo delegated GoDaddy domain-management access.
4. The remaining provider accounts, identifiers, protected variables and secrets are created.
