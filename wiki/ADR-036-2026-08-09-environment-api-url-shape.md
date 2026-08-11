# ADR-036: Environment API URL shape

**Date:** 2026-08-09  
**Status:** Accepted

## Situation

The Android application runs on tester devices and must reach the backend through a stable public
HTTPS URL. Development has no public web frontend. The future production apex domain will host a
marketing web application while also providing the production API. The previous infrastructure
plan reserved a separate production API hostname and did not define a path boundary between public
web content and backend endpoints.

## Options considered

1. Use a service-named path such as `/post-service`.
2. Use separate API hostnames for both environments.
3. Use each environment's public hostname with a stable `/api` root path.
4. Route unprefixed paths such as `/posts` directly to the backend.

## Decision

Use an environment-specific origin with the same `/api` path contract:

- development Android builds use `https://dev.yoursaynews.com/api`;
- future production Android builds use `https://yoursaynews.com/api`.

Development exposes backend endpoints only. No marketing or holding page is served at
`dev.yoursaynews.com`; its non-API root may return `404`. The future `yoursaynews.com` root will
serve the marketing web application, but that frontend is not part of the current implementation.

The deployed Quarkus service owns the `/api` root path, so the Cloudflare Tunnel can forward the
development hostname directly to the loopback-only backend listener. Endpoint paths remain domain
oriented beneath that root, for example `/api/posts`, `/api/feed`, `/api/votes` and `/api/live`.
`post-service` is a deployment name and never appears in a public URL.

## Reason

A single explicit base URL makes the native client configuration portable and removes fragile
host-plus-port concatenation. The `/api` boundary prevents future marketing routes from colliding
with backend routes and permits the production website and API to share the apex origin without
requiring the mobile app to understand infrastructure service names.

## Consequences and follow-up work

- Local development may continue to use `http://localhost:8082` without the remote `/api` prefix;
  the Expo build supplies the complete environment-specific base URL.
- Public and private deployment health checks use `/api/live`.
- Cloudflare Access must not place an interactive login challenge in front of the development API;
  application authentication and authorization protect non-public endpoints.
- When production is built, its edge/router must send `/api/*` to the backend and all intended web
  routes to the marketing frontend. No production website or production route is created now.
- Android build configuration must explicitly select the correct public API base URL. Changing
  that value requires a new native/update release appropriate to the chosen Expo distribution path.
