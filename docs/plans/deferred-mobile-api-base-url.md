# Deferred mobile API base URL migration

Status: deferred on 2026-08-09. The implementation was removed from the frontend at the owner's
request and should be restored when the Android development-store app is ready to call the remote
development backend.

## Target contract

- Development Android builds call `https://dev.yoursaynews.com/api`.
- Future production Android builds call `https://yoursaynews.com/api`.
- Local development calls `http://localhost:8082`.
- Store builds also need a public `EXPO_PUBLIC_AUTH_BASE_URL`; `localhost` on a phone refers to the
  phone, not the backend VM.
- The backend and infrastructure continue to own the `/api` path. The development hostname has no
  marketing frontend.

## Deferred implementation

Replace the split `EXPO_PUBLIC_POST_SERVICE_HOST` / `EXPO_PUBLIC_POST_SERVICE_PORT` configuration
with one complete `EXPO_PUBLIC_API_BASE_URL`. Require `EXPO_PUBLIC_AUTH_BASE_URL` in both Expo
configurations and remove trailing slashes before exposing the values through Expo `extra`.

Expose the API value as `Constants.expoConfig.extra.API_BASE_URL`, then update these service areas
to build their URLs from it:

- authentication consent and user service;
- posts, feed, and media presigning;
- profiles and social connections;
- unwrapped stories;
- user characteristics and income options;
- votes and sentiment.

Update the corresponding Jest fixtures and exact URL assertions to use the development URL above.
Also update `.env.example`, the mobile README, `scripts/smoke-test.sh`, and the API configuration
wording in `docs/plans/stage2-posts.md`.

## Files restored when this was deferred

- `frontend/mobile/your-say-news/.env.example`
- `frontend/mobile/your-say-news/README.md`
- `frontend/mobile/your-say-news/app.config.dev.js`
- `frontend/mobile/your-say-news/app.config.prod.js`
- service source and tests below `frontend/mobile/your-say-news/features/`
- `scripts/smoke-test.sh`
- `docs/plans/stage2-posts.md`

Before bringing this back, confirm the public OIDC provider URL and inject both public URLs into the
Android build environment. Then run the frontend unit tests, TypeScript check, lint, and the smoke
test.
