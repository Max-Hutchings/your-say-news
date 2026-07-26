# Service operations

This directory owns the infrastructure and deployment material for the single
`post-service` deployable. It is not another application service.

- [`deploy/`](deploy/) defines the production-shaped Docker Compose runtime and its operator
  scripts, plus reusable deployment templates when common behaviour emerges.
- [`infra/`](infra/) contains environment-specific Terraform roots and repository-local reusable
  modules.

Local development remains owned by the repository-root `compose.yaml`.
