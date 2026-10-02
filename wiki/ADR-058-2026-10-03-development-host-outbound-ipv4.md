# ADR-058 - Development host gets a primary IPv4 for outbound traffic

## Status

Accepted on 2026-10-03. Reverses the IPv6-only choice in `development.tfvars`.

## Situation

The development Hetzner VM was created IPv6-only to avoid the paid primary IPv4. The first
automatic deployment (ADR-056) failed on the VM at `docker login ghcr.io`:

```text
dial tcp 140.82.121.33:443: connect: network is unreachable
```

`ghcr.io` publishes no AAAA record, so an IPv6-only host cannot pull our snapshot images. Other
outbound dependencies (Aiven PostgreSQL, Grafana Cloud, Firebase) were never tested from the host
and may have the same gap.

## Options considered

1. Attach a primary IPv4 to the VM.
2. Use a public NAT64/DNS64 resolver on the VM.
3. Stream images to the VM over SSH (`docker save | docker load`) instead of pulling from GHCR.

## Decision

Option 1. `hcloud_ipv4_enabled = true` in `development.tfvars`.

## Reason

- Removes the whole class of IPv4-only dependency failures, not only GHCR.
- NAT64 adds a third-party relay in the path of all outbound traffic, including database and
  secret-bearing API calls.
- Streaming images bypasses the registry digest checks and still leaves the database unreachable.
- Cost is a few euros a month.

## Consequences

- Inbound exposure is unchanged: the Hetzner firewall and UFW still deny public ingress, and
  API/SSH access stays on Cloudflare Tunnel.
- Applying the change may power the VM off briefly while Hetzner attaches the address.
- Apply via the `Infrastructure Development` workflow's manual apply, then re-run the failed
  `deploy development` jobs in CI.
