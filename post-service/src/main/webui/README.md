# Your Say News admin

The TypeScript/React administration UI is built and served by Quarkus Quinoa at `/admin`.

- `src/app` composes the application.
- `src/features/auth` owns the browser Keycloak PKCE session.
- `src/features/users` owns account administration UI, state and API calls.
- `src/pages` owns route-level screens.
- `src/shared` contains domain-agnostic components and the editorial design tokens.
- Admin capabilities live in `src/features/<domain>` and export a public API from
  that feature's `index.ts`.

The default local identity configuration uses realm `your-say-news`, client `admin-client`, and
Keycloak at `http://localhost:8080`. Deployments can set `VITE_KEYCLOAK_URL`,
`VITE_KEYCLOAK_REALM`, and `VITE_KEYCLOAK_CLIENT_ID` at build time.

Use Bun for direct frontend commands:

```shell
bun install
bun run typecheck
bun run test
bun run build
```
