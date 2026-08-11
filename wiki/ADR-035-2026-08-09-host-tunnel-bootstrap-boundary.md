# ADR-035: Host tunnel bootstrap boundary

**Date:** 2026-08-09  
**Status:** Accepted

## Situation

The development VM denies all public ingress. Application deployment therefore depends on SSH
through Cloudflare Tunnel, but the original deployment skeleton ran `cloudflared` inside the same
Compose project that the deployment workflow first needed SSH access to install. That created a
bootstrap cycle: the workflow could not deploy Compose until the connector was running, and the
connector could not run until Compose had been deployed.

The infrastructure bootstrap must prepare a reusable host without embedding application images or
runtime credentials in Terraform cloud-init.

## Options considered

1. Keep `cloudflared` in application Compose and temporarily expose public SSH for every fresh VM.
2. Put the Tunnel token in Terraform cloud-init and start application Compose during first boot.
3. Install a dormant host-level `cloudflared` service through non-secret cloud-init, deliver its
   token separately during the one-time bootstrap, and keep application deployment independent.
4. Run a permanent self-hosted GitHub runner or container registry on the application VM.

## Decision

Use option 3. Terraform supplies non-secret cloud-init that creates the deployment account,
installs Docker/Compose and `cloudflared`, hardens SSH and the host firewall, and prepares the
deployment directory. The Tunnel connector runs as a dedicated host systemd service and routes the
API to its loopback-only port and SSH to the local daemon.

The Tunnel token is not included in cloud-init or application Compose. It is delivered once through
the documented bootstrap procedure and stored in a root-controlled environment file. Application
CI builds immutable private images in GitHub Container Registry and deploys their exact digests;
the VM never checks out the Git repository and does not host its own registry.

## Reason

The host connector removes the deployment cycle while preserving the deny-public-ingress design.
Keeping cloud-init non-secret avoids copying runtime credentials into Hetzner user data and
Terraform plan/state material. GHCR keeps image storage independent from the single VM, so a lost
or rebuilt host can recover without depending on services stored on that same host.

## Consequences and follow-up work

- The first infrastructure apply prepares the host but does not start the connector until its
  token is delivered separately.
- Cloudflare Access policies must protect the SSH hostname before normal deployment begins.
- Application Compose owns `post-service`, migrations and Alloy, but not `cloudflared`.
- The development application workflow publishes private GHCR commit snapshots with its
  repository-scoped workflow token, verifies an explicitly selected successful snapshot and
  deploys the exact image digests over the Tunnel. It removes the VM's transient registry
  credentials afterward. ADR-037 separates these snapshot packages from future tagged releases.
- Runtime and migration database users stay separate. A release runs forward-only migrations
  before starting the application with the lower-privilege runtime user.
- A failed application health check restores the preceding application and Alloy release when one
  exists. It never reverses a database migration, so migrations must be backward compatible.
- The API remains bound to `127.0.0.1:8082`; the host connector targets that loopback listener.
