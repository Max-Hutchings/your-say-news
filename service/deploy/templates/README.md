# Deployment templates

Repository-local templates hold Compose behaviour only after it has more than one concrete use.
The current development [`compose.yaml`](../compose.yaml) is the reference implementation.

Do not duplicate it into a generic template yet. Extract the stable single-JVM API pieces here when
another environment or application needs the same service graph.

The development application workflow packages `service/deploy/compose.yaml`, `alloy/` and
`scripts/` directly. Files below `templates/` are documentation-only and are not deployed.
