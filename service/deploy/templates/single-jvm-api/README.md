# Single JVM API template

Planned reusable boundary for a JVM API, one-shot migration, telemetry collector and outbound
Cloudflare Tunnel.

The development Compose root remains authoritative during the skeleton phase. Promote common
services into this directory only when a second concrete Compose root can prove the abstraction.

`.github/workflows/dev-app.yml` therefore does not consume this directory yet.
