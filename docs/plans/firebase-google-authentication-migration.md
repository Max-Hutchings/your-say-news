# Firebase Google authentication migration

**Status:** ready for implementation handoff  
**Decision source:** [ADR-028](../../wiki/ADR-028-2026-07-25-google-authentication-and-application-authorization.md)  
**Current external state:** the Firebase project/account exists, but Firebase is not connected to
the Expo application or `post-service`.

## Goal

Replace the remote Keycloak login path with Firebase Authentication and Google Sign-In while
keeping PostgreSQL authoritative for admission, account activity and the existing
`USER`/`OFFICIAL`/`ADMIN` account type.

A successful Google login proves identity only. It must not automatically grant an arbitrary
Google account access to Your Say News. The backend admits an identity only when it matches an
active invitation or an existing linked application account.

The browser admin application remains embedded in `post-service` through Quarkus Quinoa at
`/admin`. Do not create `admin.yoursaynews.com`, a separate admin container, a separate admin image
or another VM listener. Loading the SPA assets may remain public so login can start; every admin
API and all administrative data remain protected by the database `ADMIN` check.

## Current implementation to replace

- The Expo app uses `expo-auth-session` against Keycloak and stores Keycloak access and refresh
  tokens itself.
- After login it calls `GET /your-say-user`; that endpoint creates a PostgreSQL user by email when
  no row exists.
- Most controllers use the token principal as an email address.
- The embedded admin SPA uses `keycloak-js`, realm `your-say-news` and client `admin-client`.
- Account administration correctly checks that the database account is active and has
  `AccountType.ADMIN`, but some Unwrapped admin endpoints still depend on the Keycloak `admin`
  realm role.
- The development VM contract accepts a generic OIDC issuer/client ID. No Firebase Admin SDK,
  Firebase UID mapping, Firebase client configuration or Firebase deployment credential exists.

## Non-goals

- No production marketing website.
- No admin subdomain or standalone admin deployment.
- No application-issued access token, refresh token or session-token service.
- No Firebase custom claim as the source of `ADMIN`, official-publisher or account-active state.
- No email-only durable identity link.
- No committed service-account key, Firebase UID, tester email or token.
- Do not remove local Keycloak until Firebase migration and rollback testing are complete.

## Ownership and handoff

### Infrastructure operator - Theo

- Run and review the existing development infrastructure plan/apply pipeline.
- Create an Expo **Organization** for the shared application if the current Expo account is still a
  personal account. Do not share a personal Expo password.
- In Expo/EAS Organization settings -> Members, invite Max's own Expo account with the
  **Developer** role. That role is sufficient to create/link projects, build, publish updates and
  manage app credentials; elevate only if an operation demonstrably requires broader access.
- If an EAS project was already created under a personal account, convert that account to an
  Organization or transfer the project to the shared Organization before delivery automation is
  enabled.

### Mobile delivery implementation owner - Max

Max owns the external mobile-platform setup and all corresponding repository implementation:

1. Register/verify the Google Play developer account and complete the one-off fee where no existing
   account is available.
2. Create the Play application with the testing title **Your Say News Dev**, accept Play App
   Signing and use the repository's approved permanent Android application ID
   `com.yoursaynews.app`. The listing may be renamed **Your Say News** when the same application is
   promoted to production.
3. Complete the required Play declarations and create the internal tester email list.
4. Link `frontend/mobile/your-say-news` to the shared Expo Organization/EAS project and commit the
   generated `owner`, EAS project ID, `eas.json`, update configuration and reviewed app config.
5. Register `com.yoursaynews.app` as the development Android app in the development Firebase
   project, enable Google Sign-In, add the EAS/upload and Play App Signing SHA-1/SHA-256
   fingerprints, and supply the reviewed `google-services.json` through the approved build
   configuration.
6. Create the least-privilege Expo and Google Play automation identities/tokens and store them
   directly in GitHub/EAS secret stores.
7. Implement the Firebase backend/mobile/admin migration and tests in the phases below.
8. Create the separate mobile delivery workflow only after Expo, Firebase and Play are linked and
   a manual EAS build has proved the package/signing/configuration contract.

The existing general CI mobile test job remains the only mobile workflow until item 8. Do not add a
placeholder delivery workflow that is guaranteed to fail because project IDs, signing credentials
or external applications do not yet exist.

## External setup required before the application can be built

These are console/build-signing inputs, not application secrets to paste into source files.

1. Configure the approved permanent Android package identifier `com.yoursaynews.app`. The Expo
   configuration currently has no `android.package`; it must exactly match the Android app
   registered in Firebase and must not change after Play distribution.
2. In Firebase Project settings, register an Android app with that package identifier.
3. Enable Google under Firebase Authentication -> Sign-in providers.
4. Add the SHA-1 and SHA-256 fingerprints for every accepted signer:
   - local/development or EAS development builds;
   - the upload key where applicable; and
   - Google Play App Signing from Play Console -> App integrity.
5. Download the resulting `google-services.json`. Decide during implementation whether it is
   supplied by the build secret store or committed as reviewed non-secret environment
   configuration; never put an Admin SDK private key in it.
6. Record the Firebase project ID and generated web/server OAuth client ID used by Android
   Credential Manager.
7. Register a Firebase Web app for the embedded `/admin` SPA when its Firebase login is
   implemented. Authorize only origins that actually serve that SPA. There is no admin subdomain.
   `dev.yoursaynews.com` is the API hostname; merely adding it to Authorized domains does not
   connect the Android application or backend.
8. Create a narrowly scoped Firebase Admin service-account credential for the Hetzner workload.
   Store it directly in the approved GitHub secret store and render it as a root-owned/runtime-only
   file on the VM. Do not send it in chat, commit it, place it in Terraform variables or allow it
   into Terraform state.
9. Obtain the first administrator's Firebase UID only after a successful verified login. Bootstrap
   it using the audited operational mechanism built below; never commit the UID or email.
10. Create the application in Google Play Console before CI attempts its first store submission:
    - choose **Create app**, the public app name, default language, app rather than game, free/paid
      status and support email;
    - accept Play App Signing and complete the required app-content, privacy-policy, data-safety,
      target-audience and restricted-app-access declarations;
    - create an internal-testing tester list; and
    - grant the deployment service account only the app-level permissions needed to upload and
      release to the internal test track.
11. If this is a personal Play developer account created after 13 November 2023, plan the separate
    closed-test requirement (currently at least 12 continuously opted-in testers for 14 days)
    before applying for production access. This does not block the internal testing track.

Use separate Firebase projects/audiences for local or integration testing, development and future
production so a token issued for one environment cannot authenticate to another.

## Phase 1 - Freeze the identity and admission contracts

### Durable identity

Add an external-identity model owned by the user domain. At minimum it records:

- application user ID;
- identity issuer/provider (`firebase` plus the configured project/issuer boundary);
- case-sensitive Firebase UID from token `sub`;
- linked timestamp; and
- last verified timestamp where operationally useful.

Enforce a unique constraint over issuer/provider plus subject. Do not use email as the foreign key
or durable identifier. Email and names remain mutable profile/contact data on the user record and
may only be refreshed from a verified token according to an explicit policy.

### Invitations and bootstrap

Add an invitation/admission record sufficient to distinguish invited, redeemed, expired and
revoked access. An email invitation may be used to match the first verified login, but redemption
must atomically bind the immutable Firebase UID so later email changes cannot move access to a
different identity.

Build a one-time operational command or migration input that links the first administrator UID and
records who performed the bootstrap, when and why. It must accept sensitive input at execution
time, keep it out of logs/state/Git and refuse to overwrite an existing identity link.

### Audit

Persist an audit event for invitation, redemption, identity linking, activation/deactivation,
account-type changes and administrative access changes. Record actor, target, action, before/after
state, timestamp, request/correlation ID and a non-secret reason. Ordinary logs and telemetry must
not contain emails, Firebase UIDs or tokens.

### Existing account types

Use the currently implemented `AccountType.USER`, `OFFICIAL` and `ADMIN` model during this auth
migration. PostgreSQL remains the authority. Reconcile ADR-028's older independent-permission
wording with the later implemented ADR-034 model in the implementation PR; do not silently add a
second competing permission system as part of authentication work.

## Phase 2 - Verify Firebase identity in `post-service`

1. Add the supported Firebase Admin Java SDK behind a small application-owned verifier interface.
2. Initialise it from the configured Firebase project ID and workload credential. Fail startup in
   the remote profile when either is absent; tests use an explicit test verifier or Firebase Auth
   Emulator, never a production credential.
3. Replace the remote Keycloak bearer-token assumption with a Quarkus authentication mechanism
   that:
   - extracts exactly one bearer token;
   - verifies signature, issuer, audience/project, expiry and token shape;
   - uses the case-sensitive Firebase UID as the authenticated subject;
   - requires `email_verified` before email is used for invitation redemption/contact display;
   - never trusts a client-supplied UID/email header or request-body identity; and
   - returns `401` for invalid/expired tokens without logging token contents.
4. Apply Firebase revocation checking to security-sensitive operations such as admin changes and
   identity linking. Document the network/latency trade-off rather than enabling an expensive
   revocation lookup accidentally on every low-risk read.
5. Introduce a user-domain authenticated-identity DTO/service containing issuer, subject and safe
   verified claims. Refactor controllers and clients that currently treat
   `SecurityIdentity.getPrincipal().getName()` as an email address.
6. Give a valid Firebase identity only the broad authenticated-application capability. Resolve
   activity, account type and publishing permission from PostgreSQL for protected operations.
   Firebase custom claims must not grant product permissions.
7. Replace the mutating `GET /your-say-user` first-login behaviour with an explicit idempotent
   session/provisioning operation. It must:
   - return an existing user linked to the verified issuer/subject;
   - atomically redeem a valid invitation and create/link the application user; or
   - return `403` without creating a user when the identity is uninvited/revoked.
8. Keep status/read endpoints free of hidden writes. Return the account/onboarding state needed by
   the mobile client without exposing identity-provider subjects.
9. Convert every admin endpoint, including Unwrapped administration, to the active database
   `ADMIN` rule. Remove Keycloak-role authorization only after equivalent database checks and tests
   exist.

Use a temporary provider/profile switch if necessary so the already-distributed Keycloak client
can coexist during rollout. Keep the switch explicit and time-bounded; do not accept a token from
an unconfigured issuer.

## Phase 3 - Integrate Firebase into the Expo Android app

1. Add the chosen `android.package` and environment-specific Firebase configuration to Expo.
2. Add a maintained Expo-compatible native Firebase Authentication and Android Credential Manager
   integration. This requires a development/EAS build; do not design the feature around Expo Go.
   If no maintained adapter meets ADR-028, implement a thin Expo native module around the official
   Android Firebase Auth/Credential Manager flow rather than falling back to an embedded web login.
3. Replace `keycloakService.ts` with a provider-neutral auth boundary backed by Firebase Google
   Sign-In.
4. Let Firebase own refresh-session persistence. Remove the application-managed Keycloak refresh
   token, expiry calculation and revocation endpoint logic from Zustand/SecureStore.
5. Obtain the current Firebase ID token immediately before authenticated API work. The shared HTTP
   client attaches it as `Authorization: Bearer ...`; on one `401`, force-refresh once and retry
   once, then sign out/fail closed.
6. After Firebase login, call the explicit backend session/provisioning operation. Only set
   `isLoggedIn` after the backend admits the identity and returns the application account.
7. Preserve consent and characteristic onboarding. A Firebase account and an application account
   do not imply that onboarding is complete.
8. Implement complete logout through Firebase and clear only application cache/state owned by the
   app. Do not store or log ID tokens, Google access tokens or Firebase UIDs.
9. Restore the single mobile API base URL work in
   [deferred-mobile-api-base-url.md](deferred-mobile-api-base-url.md) as part of this migration:
   development builds use `https://dev.yoursaynews.com/api`, while local builds use the reviewed
   local endpoint. Remove Keycloak-specific auth URL configuration.

## Phase 4 - Configure Expo/EAS and Google Play delivery

Expo/EAS builds and distributes the Android binary; it is not a network proxy. Every development
store build embeds `EXPO_PUBLIC_API_BASE_URL=https://dev.yoursaynews.com/api` and calls that URL
directly over HTTPS from the user's phone.

### EAS project and build profiles

1. Link the existing Expo project to the owner's Expo account with EAS CLI and commit the generated
   EAS project ID/configuration.
2. Add `expo-dev-client` for developer-only APKs and `expo-updates` for release over-the-air
   updates. Run `eas update:configure` and review the generated update URL and runtime policy.
3. Add an `eas.json` with distinct profiles:
   - `development-client`: internally distributed APK, development client enabled, development
     Firebase project and development API;
   - `development-store`: signed AAB for Google Play internal/closed testing, `development` EAS
     Update channel and environment, development Firebase project and development API; and
   - `production`: reserved for a future production Firebase project, production API and production
     Update channel. Do not activate this profile during development work.
4. Set Android version-code management so every submitted AAB has a greater version code. Keep the
   user-visible app version and EAS runtime version deliberate and reviewable.
5. Supply `google-services.json` to EAS as a file environment variable and reference its generated
   path through `android.googleServicesFile`. Do not confuse this client configuration with the
   backend Admin SDK service-account credential.
6. Store an account-scoped or robot `EXPO_TOKEN` in GitHub Actions. Do not use a developer's
   password or interactive login in CI.
7. Configure EAS Submit with a narrowly scoped Google Play service-account key stored in EAS/GitHub
   secrets. The application must already exist in Play Console and the service account must be
   granted access to this app and internal track.

### Two delivery lanes

Use both lanes; they solve different problems.

**EAS Update - JavaScript/assets only**

- After the mobile test, typecheck and lint jobs pass on the integration branch, publish a
  non-interactive update to the `development` channel using the development EAS environment.
- Record the full Git commit SHA in the update message/metadata.
- Installed `development-store` builds download only compatible updates from that channel.
- Updates may contain JavaScript/TypeScript, ordinary styling and update-compatible bundled assets.
- Never publish directly to a future production channel from an ordinary push. Promotion remains
  explicit and uses the exact tested update.

**EAS Build + Google Play - native/runtime changes**

- Trigger a non-interactive `development-store` Android build when a change affects the native
  runtime: Expo SDK, dependencies/lockfile, config plugins, `app.json`/dynamic app config,
  `google-services.json` wiring, Android permissions, package identifier, icons/splash/native
  assets, or custom Expo/native modules.
- Give the build a new Android version code and runtime version.
- Submit the successful AAB to the Google Play internal track only after the same commit passes CI.
- Do not send native changes through EAS Update to an older incompatible build. A new store build is
  mandatory whenever native code changes.

Use an allowlist for automatic OTA publication rather than assuming every file under the mobile
directory is update-safe. If CI cannot prove a change is OTA-safe, select the build lane. EAS
runtime-version compatibility is a second safety boundary, not a replacement for this decision.

### Mobile GitHub Actions shape

Add a mobile development workflow, split into visible jobs like the application deployment
workflow:

1. **Test** - Bun frozen install, Jest, TypeScript, lint and resolved Expo config checks.
2. **Classify** - determine whether the tested commit is OTA-safe or changes the native runtime;
   fail closed to `native-build` for ambiguous changes.
3. **Publish update** - for OTA-safe integration-branch commits, run `eas update` against the
   `development` channel/environment using `EXPO_TOKEN`.
4. **Build Android** - for native-runtime commits, start an EAS `development-store` AAB build for
   the exact tested SHA.
5. **Submit internal** - submit that exact successful EAS build to Google Play internal testing;
   do not rebuild during submission.

Pull requests run Test and Classify but must not publish updates, start paid/cloud builds or submit
to Play. Protect concurrent delivery so an older workflow cannot overwrite or submit after a newer
commit. Add an explicit manual re-run/recovery path and document EAS Update rollback.

The first Play release may be uploaded manually or through EAS Submit after the app, Play API
access and service account are configured. Once the package/signing identity is established, all
subsequent development-store binaries follow the CI path. Copy the Play App Signing SHA-1 and
SHA-256 fingerprints back into the Firebase Android app; the EAS upload-key fingerprint alone is
not sufficient for Play-installed builds.

## Phase 5 - Migrate the embedded admin SPA

1. Keep the source and build under `post-service/src/main/webui` and the Quinoa UI root at
   `/admin`.
2. Replace `keycloak-js` with the Firebase Web SDK and Google provider behind the existing admin
   auth feature boundary.
3. Obtain a Firebase ID token and send it to the existing same-origin admin API paths.
4. Treat successful Firebase login as identity only. Render administrative data only after the API
   confirms the linked database account is active and `AccountType.ADMIN`.
5. Keep public SPA assets free of user/admin data and secrets. Non-admin, uninvited and inactive
   identities receive explicit restricted states and `403` responses from the server.
6. Keep the current `/admin` route and deployment shape. Do not add DNS, a Cloudflare Tunnel route,
   a web-server container or `admin.yoursaynews.com`.

If the remote profile continues to use `%prod.quarkus.quinoa.just-build=true`, remote admin UI
serving remains deliberately disabled. Enabling that embedded route is a separate reviewed
deployment decision; Firebase migration must not expose it accidentally.

## Phase 6 - Deployment configuration

1. Add non-secret Firebase project/client identifiers to reviewed environment configuration.
2. Replace remote `OIDC_AUTH_SERVER_URL`/`OIDC_CLIENT_ID` with the Firebase project and credential
   contract after the compatibility window closes.
3. Render the Admin SDK credential as a `0600` runtime file and mount/read it only in
   `post-service`. Ensure Compose output, deployment artifacts and command logs never print it.
4. Add the required GitHub secret name to `service/deploy/README.md` and the external-prerequisites
   checklist without adding its value.
5. Keep Cloudflare Access off the mobile API. Cloudflare protects transport/routing; Firebase plus
   PostgreSQL protect application access.
6. Confirm Firebase project/audience separation and reject tokens from every other project before
   switching the development API to Firebase-only.

## Phase 7 - Tests and security evidence

### Backend

- Valid Firebase token for an existing linked, active user succeeds.
- First verified login with a valid invitation creates exactly one user and one external link.
- Repeating provisioning is idempotent.
- Uninvited, expired-invitation, revoked and inactive identities are rejected without creating a
  user.
- Invalid signature, issuer, audience, expiry and missing/false `email_verified` cases fail with
  exact `401`/`403` contracts.
- The same email with a different Firebase UID cannot take over an existing link.
- Concurrent invitation redemption cannot create duplicate users/links.
- `USER` and `OFFICIAL` accounts cannot access admin APIs; an active database `ADMIN` can; an
  inactive/revoked admin cannot.
- Firebase/custom token claims cannot override database account type or publisher status.
- API/admin DTOs and logs do not expose Firebase UID, tokens or unrelated PII.

Use plain unit tests for claim/admission decisions and `@QuarkusTest` plus real PostgreSQL for
persistence, uniqueness, transaction and authorization behaviour. Firebase itself may be replaced
by a narrow verifier test double in domain integration tests; add at least one adapter contract
test with the Firebase Auth Emulator or controlled signed fixtures so SDK wiring is not entirely
mocked away.

### Mobile and admin frontends

- Google login success, cancellation and provider failure.
- Backend admission success and uninvited/inactive rejection.
- Bearer attachment, one forced-refresh retry and fail-closed logout.
- Returning-session hydration without storing raw tokens in Zustand/SecureStore.
- Consent/onboarding routing after admitted login.
- Admin login followed by database `ADMIN`, non-admin and inactive states.

Run the repository `test-audit` skill after changing tests. Finish with backend checks, mobile and
admin tests, TypeScript checks, lint, production builds and the deployment contract tests.

## Rollout order

1. Apply backward-compatible identity/invitation/audit migrations.
2. Deploy backend Firebase verification and provisioning while preserving the explicit temporary
   Keycloak compatibility path if an existing store build still needs it.
3. Bootstrap the first Firebase-linked administrator through the audited operational path.
4. Release an internal Android Firebase build and verify login, provisioning, refresh, logout,
   onboarding and API access with invited test accounts.
5. Verify database deactivation and admin revocation take effect while Firebase tokens remain
   otherwise valid.
6. Migrate the embedded admin SPA authentication without changing its route/deployment shape.
7. Remove the remote Keycloak acceptance/configuration only after the Firebase build is deployed,
   rollback has been exercised and old clients are no longer supported.
8. Remove local Keycloak in a later cleanup only when local Firebase/emulator development has an
   equally reliable replacement.

## Definition of done

- An invited Google user signs into the Android app through Firebase and is linked once to a
  PostgreSQL application account by Firebase UID.
- An arbitrary Google account cannot create or access an application account.
- Every protected request validates a Firebase ID token for the correct environment and then
  applies current PostgreSQL activity/permission rules.
- Deactivation and admin/account-type changes take effect without changing Firebase claims.
- The app does not own refresh tokens and no auth token/UID/email leaks into Git, Terraform state,
  logs, metrics or deployment artifacts.
- The Play-installed development build calls `https://dev.yoursaynews.com/api`, receives compatible
  JavaScript/assets releases from the EAS `development` channel and never receives an incompatible
  native update.
- Mobile CI publishes OTA-safe commits only after tests pass; native changes produce a new signed
  AAB and submit the exact tested build to Google Play internal testing.
- The embedded `/admin` application uses the same Firebase identity boundary and database `ADMIN`
  authority, with no new hostname or deployable.
- Tests prove invalid-token, identity-linking, invitation, revocation and authorization boundaries,
  and the independent test audit reports no unresolved high-severity gap.
