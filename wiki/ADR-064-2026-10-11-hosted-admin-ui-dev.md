# ADR-064: Hosted admin UI on dev

## Situation

The admin SPA (ADR-033) and its cookie session only ran on laptops. Hosted images built the SPA but
did not serve it (`quarkus.quinoa.just-build=true`), and the session beans were
`@IfBuildProfile("local")`. ADR-055 left "serving it on dev" as a separate decision.

Admins now need to manage accounts, topics, auto-post and Unwrapped on the dev environment, where
real Google accounts (including Google Play's pre-launch robots) create users.

Constraints:

- One image is promoted from dev to prod (ADR-037), so build-time settings cannot differ between them.
- The backend owns the `/api` root path on hosted environments (ADR-036). The Cloudflare Tunnel
  forwards the whole `dev.yoursaynews.com` hostname to it.
- Dev uses the real Firebase project `your-say-news-development` with Google sign-in, not the
  emulator's email/password accounts.

## Options considered

1. Keep the admin UI laptop-only and point a laptop at the dev database.
2. Deploy the admin SPA separately (for example a static host) against the dev API.
3. Serve the admin SPA from `post-service` in every image, load its Firebase settings at runtime, and
   enable the cookie session outside tests.

## Decision

Option 3.

- `quarkus.quinoa.just-build=false` in base. The SPA is served at `/admin/` on a laptop and at
  `https://dev.yoursaynews.com/api/admin/` on dev. Admin APIs therefore live at `/api/api/admin/*`
  on dev. The SPA derives that prefix from its own URL.
- The SPA is built with page-relative asset URLs (`base: "./"`), so one bundle works under both roots.
- `GET /api/auth/admin/config` (public) returns the Firebase web settings and sign-in method
  (`PASSWORD` on a laptop with the emulator URL, `GOOGLE` when hosted). They are runtime config:
  `firebase.web.api-key` and `firebase.web.app-id` per environment. Both are public identifiers.
- The admin session classes move from `@IfBuildProfile("local")` to `@UnlessBuildProfile("test")`.
- Admin cookies are `Secure` by default (`firebase.auth.secure-cookies=true`); only `%local` turns it off.
- `firebase.auth.allowed-origins` has no base value. `%dev` sets `https://dev.yoursaynews.com`, so an
  environment that forgets it fails startup rather than accepting cross-site admin mutations.
- The CSRF filter now recognises `/api/admin/...` paths. Before this change, JAX-RS's leading slash
  meant the check never ran. The filter is loaded in tests too; `firebase.auth.csrf-enforced` is
  `true` everywhere except `%test`, and `AdminCsrfProtectionTest` re-enables it under the hosted
  `/api` root path to prove real requests are refused.

## Reason

- One deployable and one image keep ADR-033 and ADR-037 intact; no new hosting to secure.
- Runtime config is the only way to give dev and prod different Firebase web settings from one image.
- Authorisation does not depend on hiding the UI: every admin API still requires an authenticated
  session and the active database `ADMIN` check (ADR-034).

## Consequences and follow-up

- The admin login surface is public on dev. Protection is Google sign-in, the database admin check,
  HttpOnly `SameSite=Strict` `Secure` cookies and CSRF tokens bound to the dev origin.
- Firebase console setup for each hosted environment: a Web app registration and the hostname under
  Authentication > Settings > Authorized domains.
- The first dev admin was promoted by a one-off SQL update; there is no automated bootstrap.
- Prod will serve the admin UI too once it exists. It must set its own allowed origin and Firebase
  web settings, or startup fails.
- Admin API URLs on hosted environments carry a doubled `/api/api/` prefix. Renaming the admin
  controllers to drop their own `/api` prefix would remove it, but changes every admin route.
- Supersedes the ADR-055 line "The admin web UI and its cookie session stay laptop-only".
