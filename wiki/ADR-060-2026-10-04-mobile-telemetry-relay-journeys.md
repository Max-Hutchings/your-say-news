# ADR-060 - Mobile telemetry relay and click journeys

Date: 2026-10-04

## Situation

The Expo app sent no telemetry. post-service already exports metrics, logs and traces through
OpenTelemetry (otel-lgtm locally, Alloy to Grafana Cloud on dev), but nothing showed what a person
did in the app: which screens they opened, what they tapped, which calls failed on the device, or
where the app crashed.

We need enough detail to follow one person's click journey through every core screen. When a user
reports a problem, support must be able to find that user's journey. The product's privacy promise
must still hold: no vote or characteristic is ever logged next to an identity.

The app cannot reach a collector on dev: Alloy only listens on the server's private Docker network,
and Grafana Cloud's OTLP credentials must not ship inside a public app.

## Options considered

1. **OpenTelemetry JS SDK in the app, exporting OTLP straight to a public collector endpoint.**
   Standard, but it exposes an unauthenticated ingest endpoint (or a Grafana Cloud token) to the
   internet, and the browser-oriented SDK fits React Native poorly (no zone context, partial
   performance APIs).
2. **A third-party mobile product (EAS Observe, Sentry).** Good crash reporting, but the journey
   would live outside Grafana, could not join post-service traces, and adds a new processor of user
   behaviour data.
3. **A small in-app recorder that uploads typed events to post-service, which relays them as OTLP.**
   Uploads use the existing Firebase auth (since ADR-061 also accepted before sign-in, without a
   user id). The server checks every value against allowlists before
   anything becomes a metric label or a log line.

## Decision

Option 3.

- **App (`features/telemetry`)** records `app_start`, `app_state`, `screen_enter` / `screen_exit`
  (every expo-router route, automatically, as its route template such as `/posts/[postId]`),
  named taps (`trackAction("vote.cast", postId)`), every post-service call, and crashes (error
  boundary plus the global JS handler). Events are batched to `POST /telemetry/mobile` every 10 s, on
  background, and on crash. Unsent events are kept in AsyncStorage and sent on the next launch.
- **Trace model:** one trace per screen view. Taps and API calls are child spans of the screen span.
  API calls carry a W3C `traceparent` header, so post-service's own spans join the same trace in
  Tempo.
- **post-service (`com.yoursay.platform.mobiletelemetry`)** checks each event, records Prometheus
  metrics (`yoursay_mobile_*`), and writes OTLP/JSON spans and logs under
  `service.name=your-say-news-mobile` to the collector HTTP receiver
  (`mobile-telemetry.otlp.endpoint`). It writes OTLP directly, because the OpenTelemetry Java API
  cannot create spans with the ids the device already chose.
- **Journey:** every log line carries `session.id`, which is random per app launch. The "Your Say
  News - Mobile app" dashboard lists recent sessions and replays one session's journey from Loki,
  with links to its screen traces.
- **User link:** see "Linking journeys to users" below.

## Linking journeys to users

### Situation

Without a user link, a session can only be tied to an account by accident: post-service's own logs
in the same trace sometimes print the caller's internal user id. Support needs to answer "what
happened to this user?" on purpose, not by luck.

### Options considered

1. **No user link.** Strip user ids from backend logs or drop `traceparent`. Support cannot find a
   reported user's journey, and losing `traceparent` breaks the app-to-backend trace.
2. **A pseudonym:** a keyed hash of the user id. Hides the id from someone casually reading Grafana,
   but support needs the key to search, and it is still personal data under GDPR.
3. **The user's main id:** the internal numeric account id (the user table's primary key).

### Decision

Option 3. This is the industry standard: Sentry, Datadog Real User Monitoring, Firebase Crashlytics
and New Relic all attach the internal user id to sessions so support can debug a reported problem.

- post-service adds the internal user id to every span and log record as `user.id` (Loki:
  `user_id`). `MobileTelemetryServiceImpl` resolves it with `YourSayUserService.getAccessByEmail` for
  the authenticated uploader, never from the app's request body. The app's payload does not change.
  A signed-in identity with no account yet is recorded without a `user.id`.
- The Mobile app dashboard has a "User id" box that filters "Recent sessions" to one person.
- Never the email, name, date of birth or Firebase UID.
- `user.id` is a log and trace attribute only. It is never a metric label (unbounded).
- Backend logs that print the internal user id may stay. They now match the documented design.
- Trace and span ids are unrelated to the user. The device generates them randomly for each screen
  view, and `session.id` randomly for each app launch. None is derived from the user id.

### Reason

- Support can search Grafana by user id and see every session and screen trace for that person,
  including the post-service work behind each call.
- An internal id is a pseudonym, not a direct identifier. Mapping it to a person needs database
  access, which is a separate and narrower permission than Grafana.

### Controls

Under GDPR an internal user id is still personal data, so the link is allowed only with:

- **Legal basis:** legitimate interest (debugging and support). The privacy policy must say the app
  records usage and crash diagnostics linked to the account.
- **Minimisation:** the special-category boundary (GDPR Article 9) holds. Political leaning and
  other characteristics, vote choices and the Unwrapped follow-up answer are never recorded. A
  journey may show that a user voted on story 42, never how.
- **Retention:** Loki and Tempo keep this data for 30 days at most, then delete it automatically.
  This also covers erasure requests without a manual purge.
- **Access:** Grafana access is limited to the people who support and operate the service.

## Reason

- One place to look. Mobile and backend telemetry share Grafana, the `domain` / `operation` /
  `outcome` vocabulary, and one trace per screen.
- No new public ingest surface and no secrets in the app.
- Server-side allowlists (screens, actions, platforms, id formats) keep metric labels bounded even
  against an old or tampered client.

## Privacy rules

- The app never sends a user id, email, chosen vote option, Unwrapped follow-up answer or
  characteristic answer. The only identity is the `user.id` that post-service adds from auth.
- Another member's id (profile route params, follow targets) is never recorded.
- A tap's `target` is a story id, a topic tab id, a step number or a UI option key only.
- API call paths are templated twice: on the device (`/social/follows/{id}`) and again by the server
  route allowlist. Raw paths are never stored.
- Crash messages are scrubbed of emails and long numbers and cut to 300 characters before logging.
- App log records (`log` events, ADR-061) follow the same rules: allowlisted names and attribute keys,
  code-shaped attribute values, scrubbed console text.
- Metric labels never carry a session id, story id or target. Those appear only in logs and traces.

## Consequences

- Follow-up work for the user link:
  - Set 30-day retention on Loki and Tempo, locally and in Grafana Cloud for dev.
  - Add the diagnostics line to the privacy policy before the link ships to prod.
- A new tap needs its name added to both `features/telemetry/vocabulary.ts` and
  `MobileVocabulary.java`; otherwise it is counted as `other`.
- A new screen is tracked automatically, but appears as `other` in metrics until its template is
  added to `MobileVocabulary.SCREENS`. Logs and traces still show it.
- Device clocks are trusted within -24 h / +5 min. Mobile and server spans in one trace can be
  offset by the device's clock drift.
- `compose.yaml` provisions `grafana/dashboards/mobile-app.json` only into the local otel-lgtm stack.
  Nothing in this change provisions it into Grafana Cloud for dev.
