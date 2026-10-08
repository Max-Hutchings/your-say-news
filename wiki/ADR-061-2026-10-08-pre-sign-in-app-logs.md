# ADR-061 - Pre-sign-in app telemetry and app logs in Loki

Date: 2026-10-08

## Situation

- Google sign-in fails on Android before the app ever calls post-service. The only record was a
  `console.warn` line (stage + provider code, from "Sign-In Diagnostics"), which stays on the device.
- ADR-060's relay (`POST /telemetry/mobile`) required the `user` role, and the app held every event
  until someone was signed in. A failed sign-in never produces a Firebase token, so exactly the
  journeys support needs most were never uploaded.
- No app log line (`console.warn` / `console.error`) reached Grafana at all.

## Options considered

1. **Keep the relay authenticated; upload pre-sign-in events after a later successful sign-in.**
   Loses every journey where sign-in keeps failing - the case being debugged.
2. **A separate public "diagnostics" endpoint.** Two ingest paths with the same sanitising; more
   surface for no gain.
3. **Open the existing relay to callers who have not signed in, with a service-wide anonymous
   budget, and add a `log` event type.** One pipeline, one allowlist, same Loki/Tempo/Prometheus
   shape.

For logs:

- **Forward every console level, raw.** console.log/info are high-volume debug output from our code
  and libraries, and raw arguments can be objects such as a user profile or a Firebase error whose
  `customData` holds the email.
- **Forward warn/error only, with arguments reduced to text, plus structured records for known
  diagnostics.** Chosen.

## Decision

Option 3.

- **Relay:** `POST /telemetry/mobile` is `@PermitAll` and listed in
  `quarkus.http.auth.permission.public.paths`. A bearer token is still verified when present; only a
  verified identity adds `user.id` (ADR-060). Anonymous batches carry no user id and never touch the
  user tables.
- **Abuse limit:** anonymous batches share a global budget,
  `mobile-telemetry.anonymous.max-batches-per-minute=300` (`AnonymousUploadBudget`). Over budget the
  batch is answered `202` with `accepted=0` (so the app does not retry into the flood) and counted
  as `yoursay_mobile_events_dropped_total{reason="anonymous_rate_limited"}`. Global, not per client,
  because behind Cloudflare Tunnel there is no trustworthy client address without extra proxy
  config. Existing limits still apply: 200 events per batch, field size limits, allowlists.
- **App:** uploads start at launch, with or without a token (`canUpload` removed).
- **`log` event:** `{ level: info|warn|error, name, message?, attributes? }`.
  - `logEvent("warn", "auth.sign_in_failed", { stage, code })` - structured, sent at once, and printed
    to logcat as `auth.sign_in_failed stage=google code=10`. Used by `firebaseService` (google,
    firebase stages) and `authContext` (server stage).
  - `console.warn` / `console.error` are captured as `name="console"`. Arguments become text:
    strings, numbers, booleans and `Error` name/message only; objects and arrays become `[object]`.
    Capped at 100 per launch and sent with the next timed flush.
  - `console.log` / `console.info` are **not** forwarded: noisy, and the failures they describe
    (`UserService` request errors) already arrive as `api_call` events with status and outcome.
- **Server sanitising:** `name` must be in `MobileVocabulary.LOG_NAMES` (else `other`). Attributes
  keep only keys in `LOG_ATTRIBUTE_KEYS` (`stage`, `code`) whose values match
  `[A-Za-z0-9_./:-]{1,64}` - a code, never prose or an email. `message` goes through the crash
  scrubber (emails, opaque ids, long numbers removed; 300 characters max).
- **Output:** one OTLP log record per event under `service.name=your-say-news-mobile`, severity
  from `level`, attributes `app.log.name` and `app.log.<key>` (Loki: `app_log_name`,
  `app_log_stage`, `app_log_code`), linked to the current screen's trace. Counted as
  `yoursay_mobile_logs_total{platform, environment, level, log_name}`.

Find sign-in failures in Grafana (Loki, dev):

```logql
{service_name="your-say-news-mobile"} | app_log_name="auth.sign_in_failed"
```

## Reason

- The failing sign-in journey now reaches Grafana with the stage and provider code, and the
  session's screens and API calls around it.
- Metric labels stay bounded: `level` and `log_name` are allowlisted; codes and messages live only
  in Loki.
- No PII: no user id before sign-in, no serialised objects, attribute values restricted to code
  shapes, messages scrubbed.

## Consequences

- The relay is a public write endpoint. The global budget protects Grafana Cloud's quota, but one
  noisy client can spend it for everyone. Before prod: per-client limits (Cloudflare rate limiting
  or trusted `CF-Connecting-IP`) and an app attestation check should replace or back the global cap.
- Captured console text can still contain a name a developer interpolated into a message. Rule for
  code: never put personal data in `console.warn` / `console.error`; use `logEvent` with codes.
- A new structured diagnostic needs its name in both `features/telemetry/vocabulary.ts`
  (`TELEMETRY_LOG_NAMES`) and `MobileVocabulary.LOG_NAMES`, and any new attribute key in both
  `TelemetryLogAttributes` and `LOG_ATTRIBUTE_KEYS`.
- The Mobile app dashboard has a collapsed "App logs and sign-in failures" row; dropped events are
  split by `reason`.
