# Mobile Frontend Hardening

A fix-and-cleanup pass over the Expo mobile app (`frontend/mobile/your-say-news`) to stabilise
what exists **before** the next feature lever is added. Scope is **fix-only**: repair broken
wiring, finish half-built auth, and bring the code back in line with `CLAUDE.md`. New product
surfaces (the aggregated-sentiment results screen, profile/settings screens) are explicitly
**deferred** to their own feature plans — but the API contracts they will depend on are designed
here so we don't paint ourselves into a corner.

## Goal

After this work the app should: render correctly on native (not just web), keep a user signed in
across token expiry, drive every URL from config, never log secrets, and have its onboarding flow
living in `features/` as thin-route + small-component code with theme tokens throughout — all
under test.

## Affected areas

- `app/` — routing/layout reconciliation, About screen rewrite, thinning the onboarding route.
- `features/auth/` — token refresh, single HTTP client, config-driven Keycloak, full logout.
- `features/user-characteristics/` — absorb the onboarding monolith (data, components, styles).
- `constants/theme/` — consumed (not changed) to replace hardcoded colours.
- Tooling — add `jest-expo` + React Testing Library.
- Backend: **no changes in this pass.** Contract design only (the characteristic service does not
  exist yet); actual endpoints land with their own feature plans.

---

## Phase 0 — Correctness (P0: things that are broken)

1. **Reconcile the protected tab navigator.** `app/(protected)/_layout.tsx` declares tabs
   `home`/`profile`/`settings` that have no route files, while the real `index.tsx` (Home) has no
   tab entry, and `(usercharacteristics)` / `about` are routes that shouldn't be bottom tabs.
   Decide the real tab set (at minimum: Home), register only screens that exist, and route
   onboarding/about as stacked (non-tab) screens. Pull `tabBarActiveTintColor` etc. from
   `constants/theme`, not `#007AFF`.

2. **Rewrite `app/(protected)/about/your-say-news.tsx` in RN primitives.** It currently uses
   `<div>`/`<h1>`, which crash on native. Use `ThemedView`/`ThemedText`.

3. **Fix `accessTokenExpired()` in `features/auth/services/authContext.ts`.** The helper calls
   itself (infinite recursion). Implement it against `accessTokenExpiresAt`, and actually
   **populate `accessTokenExpiresAt` at login** from the token response `expiresIn`.

4. **Drive Keycloak from config.** `keycloakService.ts` hardcodes `http://localhost:8080/...` and
   the client id. Read `KEYCLOAK_BASE_URL`/`KEYCLOAK_REALM`/`KEYCLOAK_CLIENT_ID` from
   `Constants.expoConfig.extra` (already defined in `app.config.dev.js`; add to
   `app.config.prod.js`).

5. **Strip all secret logging.** Remove `console.log` of tokens in `keycloakService.ts` and of the
   whole store in `authContext.login`, plus the `Root Layout:` log and other stray logs.

## Phase 1 — Auth hardening (P0/P1)

6. **Implement refresh-token flow.** On a request that hits an expired access token (or a 401),
   exchange the stored refresh token at the Keycloak token endpoint, update the store, and retry
   once; only log out if the refresh itself fails. `offline_access` is already requested.

7. **Consolidate to one HTTP client.** Today both `YsnHttpClient` (axios, re-parses the persisted
   SecureStore blob per request) and `useFetchWithAuth` (fetch) exist. Keep one (recommend the
   axios client with an interceptor that reads the in-memory store and owns the refresh logic),
   delete the other, and update `features/auth/index.ts` exports.

8. **Make logout complete.** Clear `id` and `hasOnboarded` along with the tokens, and revoke the
   session/refresh token at Keycloak's end-session/revocation endpoint.

## Phase 2 — Structure & theme (P1)

9. **Move the onboarding monolith into the feature.** `app/(protected)/(usercharacteristics)/usercharacteristics.tsx`
   is ~1500 lines and breaks "`app/` is routes/layouts only" and "one component per file". Split:
   - option datasets (countries, UK counties, income, …) → `features/user-characteristics/data/`
   - `SectionCard`, `Label`, `ChipRow*`, `Dropdown`, `ScaleSelector` → one file each under
     `features/user-characteristics/components/`
   - the screen/step orchestration → a `features/user-characteristics` component exported from its
     `index.ts`; the route file becomes a thin wrapper.

10. **Replace hardcoded colours with theme tokens** across the onboarding screen, `SelectableChip`,
    and the tab bar. No magic hex values (CLAUDE rule).

11. **Fix `User.dateOfBirth` type** — it arrives as a string from JSON, not a `Date`.

12. **Delete Expo boilerplate** not used by the product: `hello-wave.tsx`,
    `parallax-scroll-view.tsx`, `external-link.tsx`, and the react-logo assets. Strip the
    "usage example" comment blocks from `use-posts-api.ts` / `use-fetch-with-auth.ts`.

## Phase 3 — Contract design only (no backend work)

13. **Design the characteristic submission contract with PII separation baked in.** The current
    onboarding payload bundles `userId` with every characteristic in one object, violating the
    core rule that identity must never travel alongside characteristic data. Specify a contract
    where the authenticated identity is carried by the bearer token only and the body contains
    *just* the characteristic answers — documented here, implemented when the characteristic
    service is built. Wire the onboarding screen's submit to this shape (behind the existing
    config-driven service host) so the `// TODO: send payload` is removed even if the endpoint is
    stubbed.

> The aggregated-sentiment **results screen**, **profile**, and **settings** screens are out of
> scope for this pass and will each get their own plan.

## Phase 4 — Testing & polish (P1)

14. **Add the test runner.** Install `jest-expo` + `@testing-library/react-native`, add a `test`
    script and jest config.
15. **Cover the critical paths:** auth store (login success/failure, refresh, expiry, logout
    clearing state), the onboarding flow (required-field gating, step navigation, submit shape),
    and the UI primitives (`Button` variants/disabled/loading, `SelectableChip` toggle).
16. **Run the `test-audit` skill** to confirm the tests pin expected values and would fail if the
    code broke.
17. **Lint/format pass:** remove unused destructures (`colors`/`Typography` in sign-in & splash),
    normalise file naming and indentation.

## Test approach

React Testing Library per `CLAUDE.md`: assert on what the user sees/does and on store state
transitions, with representative data and expected-value assertions (not "not null"). Auth network
calls (Keycloak token/refresh) are mocked at the HTTP-client boundary; the store logic itself is
tested for real.

## Out of scope / follow-ups

- Aggregated-sentiment results screen (the core product surface) — its own plan.
- Profile & settings screens.
- Real characteristic-service and post-service endpoints (backend) — the mobile side is wired to
  config-driven hosts here so it connects once those exist.
