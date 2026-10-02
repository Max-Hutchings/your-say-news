# ADR-057 - Android Play Store release via EAS

## Status

Accepted on 2026-10-02. First release: build `a78ecd64-1ee0-4414-a8ee-d17d799c52d4` (versionCode 2)
submitted to the Play internal track as a draft (submission `61338ab1`).

## Situation

- The backend deploys to dev through CI, but the Expo app had never been uploaded to Google Play.
- `eas.json` already had a `development-store` build profile (Android App Bundle, dev API).
- No submit profile, no Play service account, and no documented release steps existed.

## Options considered

1. Build `.aab` with EAS, upload by hand in Play Console every release.
2. Build with EAS and submit with `eas submit` using a Play service account.
3. Fully automate build and submit with an EAS Workflow on push.

## Decision

Option 2 now. Option 3 once the first release has worked by hand.

## Reason

- `eas submit` removes the manual upload after a one-time service account setup.
- Doing the first release by hand makes problems easier to see before automating them.

## Steps taken (learning)

### Tooling

- Installed the EAS CLI with `bun add -g eas-cli`.
- Added `export PATH="$HOME/.bun/bin:$PATH"` to `~/.zshrc` so Bun global tools are found.
- The Claude `!` prompt does not load `~/.zshrc`; use `~/.bun/bin/eas` there.
- Ran `bun install` in the app folder; stale `node_modules` was missing the Google Sign-In plugin
  and broke config loading.

### Expo account

- `eas login` (opens the browser).
- The project is owned by the `your-say-news` Expo organisation, not a personal account.
  Logging in as a personal account gave "Entity not authorized" until the org invite was accepted.
- Expo organisations are free.

### Repo changes

- `eas.json`: added a `development-store` submit profile, track `internal`, release status `draft`.
  Google only accepts draft releases until the app has been published once.
- `app.json`: display name changed to `Your Say News`.
- `.gitignore`: added `*service-account*.json` so the Play key cannot be committed.

### Firebase config (`google-services.json`)

- Downloaded from Firebase: project `your-say-news-development` > Project settings > Your apps >
  Android `com.yoursaynews.app`.
- It is client config, not a secret (it ships inside the app). It is still git-ignored.
- Do not confuse it with the Firebase Admin service account JSON (contains `private_key`), which is
  backend-only.
- Stored in EAS as file variable `GOOGLE_SERVICES_JSON` (environment `development`).
- A secret EAS variable cannot be changed to sensitive; it must be deleted and recreated.
- Kept a local copy in the repo root because `eas` loads the app config on the laptop first.

### Local config check gotcha

- Every `eas` command loads `app.config.js` locally and EAS ignores `.env`.
- Without `APP_ENV`, `app.config.dev.js` needs `EXPO_PUBLIC_POST_SERVICE_HOST` and `_PORT`.
- With the `development-store` profile, `app.config.prod.js` (now `app.config.hosted.js`) needed `GOOGLE_SERVICES_JSON` as a
  local file path. EAS does not download file variables locally, even sensitive ones.
- Working command:

  ```bash
  GOOGLE_SERVICES_JSON=../../../google-services.json \
  EXPO_PUBLIC_POST_SERVICE_HOST=x EXPO_PUBLIC_POST_SERVICE_PORT=0 \
  eas build -p android --profile development-store
  ```

### Build

- `eas build` reused the Android keystore already stored on EAS from an earlier
  `development-client` build. Version code auto-incremented to 2.

### Google Play

- App `com.yoursaynews.app` created in Play Console (draft).
- Google Cloud service account `id-eas-play-submit@your-say-news.iam.gserviceaccount.com`,
  no Cloud roles needed.
- Invited its email in Play Console > Users and permissions with testing-track release rights.
- Downloaded its JSON key, renamed to `google-service-account.json` in the repo root (git-ignored).
  The downloaded name is not ignored, so always rename it.
- Uploaded the key to EAS with `eas credentials -p android` > Google Service Account >
  Set up a Google Service Account Key. Pick "Set up", not "Select an existing".
- Enable the **Google Play Android Developer API** in the service account's Google Cloud project.
  The first `eas submit` failed with `PERMISSION_DENIED: Google Play Android Developer API has not
  been used in project ... or it is disabled` until it was enabled.
- In Play Console > Users and permissions, give the service account app permissions (release to
  testing tracks, manage testing tracks, view app information) and press Save. Without this the
  submit fails with `The service account is missing the necessary permissions`.

## Follow-up work

- Roll out the draft release in Play Console > Test and release > Internal testing, and add testers.
- Add Play's app signing key SHA-1 and SHA-256 (Play Console > App integrity) to the Firebase
  Android app, or Google Sign-In fails with `DEVELOPER_ERROR` on Play-installed builds.
- Done in ADR-059: `app.config.hosted.js` requires `GOOGLE_SERVICES_JSON` only on the EAS builder.
- Complete the Play store listing, content rating, Data safety, privacy policy and reviewer
  sign-in access before promoting beyond internal testing.
- Add an EAS Workflow to build and submit automatically.
