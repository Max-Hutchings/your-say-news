# ADR-033 — Admin UI inside post-service

## Situation

Your Say News needs a browser-based administration workspace for future user and site management.
The repository currently has one backend deployable, `post-service`, and a separate Expo mobile
application. The first admin increment should establish the web toolchain and visual foundation
without adding administrative capabilities or exposing site data.

## Options considered

1. Create a separately deployed admin frontend.
2. Add server-rendered administration pages to Quarkus.
3. Add a TypeScript/React single-page application to `post-service` and build and serve it with
   Quarkus Quinoa.

## Decision

Use option 3.

The admin application lives in `post-service/src/main/webui`, is mounted at `/admin`, and uses Vite
for local development and production builds. Quinoa manages the Vite process during Quarkus dev
mode and packages the built assets with `post-service`. The web app reuses the authoritative
editorial colours and typography from the mobile application.

The initial placeholder route is public because it contains no administrative controls or data.
ADR-034 adds browser authentication and database-backed authorization for the first real
administration capability.

## Reason

Co-locating the UI with the sole backend deployable keeps the initial operational shape simple and
lets Quinoa provide one development and release lifecycle for Java and TypeScript. React and
TypeScript match the existing frontend skills and conventions, while a separate `webui` source tree
keeps browser concerns out of backend domains.

## Consequences and follow-up

- `/admin` is the stable root for the administration workspace.
- The managed Vite server uses port 8083 so it can run alongside the Expo web server on port 5173.
- Future UI capabilities should be grouped by domain under `src/features` and expose a small public
  API through each feature's `index.ts`.
- Browser assets remain public so the SPA can initiate OIDC login. Administrative data and actions
  are protected by authenticated APIs and the active database-admin check defined in ADR-034.
- Backend administration endpoints use `/api/admin/*`, outside Quinoa's `/admin` SPA root, so Vite
  development proxying cannot loop API requests back through the SPA handler.
