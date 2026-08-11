---
name: instrument-app-observability
description: Plan, implement, and review Your Say News metrics, structured logs, traces, error classification, and Grafana dashboards. Use before adding or changing production code, domains, APIs, internal service operations, background jobs, database or storage access, external integrations, or observability configuration so every important path has time-aware traffic, latency, and failure coverage without exposing PII or creating high-cardinality telemetry.
---

# Instrument App Observability

Preserve complete observability whenever production behaviour changes. Instrument meaningful
boundaries and business operations, not trivial helpers or pure calculations.

## Workflow

1. Identify the owning domain and stable operation name for every changed execution path.
2. Check which HTTP, JVM, REST client, datasource, and trace signals Quarkus already emits before
   adding custom instrumentation.
3. Add only the custom counters, timers, spans, and structured logs needed to cover domain work,
   internal calls, dependencies, jobs, and failures.
4. Add or update the overall and owning-domain Grafana dashboards in the same change.
5. Verify emitted telemetry and dashboard queries using successful, rejected, and failed requests.

## Error classification

- Count every 5xx response as an error and show 5xx responses in their own panel.
- Count a 4xx response as an error by default. Classify it as an expected rejection only when the
  endpoint contract explicitly defines that outcome as normal, such as a wrong password.
- Track expected rejections separately so they remain visible without inflating the error total.
- Count internal service failures, dependency failures, timeouts, and failed background jobs as
  errors even when no HTTP 5xx response is produced.
- Do not count a warning as an error unless the operation actually failed.
- Group Top 10 errors by a stable error code or exception type. Never group by raw error message or
  stack trace.
- Do not decide whether an outcome is expected from its HTTP status alone. Use the endpoint or
  operation contract.

Use these stable outcome classes where applicable:

- `success`
- `expected_rejection`
- `unexpected_client_error`
- `server_error`
- `service_error`
- `dependency_error`
- `job_error`

## Metric and logging rules

- Measure traffic, errors, and latency for every public API route.
- Measure calls, errors, and latency for important internal domain operations that have no public
  API.
- Measure p50, p95, and p99 latency for APIs, important operations, and dependencies.
- Use consistent low-cardinality attributes: `domain`, `operation`, `outcome`, `error_type`,
  `error_code`, and `environment` where relevant.
- Use stable route templates and operation names. Never use raw URLs containing identifiers.
- Never put user IDs, post IDs, vote IDs, request IDs, emails, tokens, PII, raw error messages, or
  other unbounded values in metric attributes.
- Write structured warning and error logs containing the domain, operation, outcome, stable error
  code, and trace ID.
- Keep sensitive request data, credentials, voting data, and personal characteristics out of logs.
- Correlate metrics, logs, and traces so a dashboard error can lead to its trace and related logs.

## Time-aware Grafana queries

- Use `$__rate_interval` for rates and latency histogram calculations.
- Use the selected `$__range` with `increase(...)` for totals and Top 10 panels.
- Do not use fixed windows such as `5m`, `1h`, or `24h` in dashboard queries.
- Make every panel respond to the Grafana dashboard time picker.
- Provide variables for environment, service, domain, and operation where they improve filtering.

## Overall dashboard

Show:

- Requests per second.
- Total requests during the selected period.
- Total errors during the selected period.
- Errors per second and error percentage.
- 5xx responses in a dedicated panel.
- Unexpected 4xx responses in a dedicated panel.
- Expected rejections separately.
- Top 10 errors during the selected period.
- Request latency at p50, p95, and p99.
- Responses grouped by status class and the slowest API routes.
- Dependency health and JVM CPU, memory, garbage collection, and thread usage.

Remove stale service assumptions from existing dashboards. `post-service` is the sole backend
deployable; its business domains require domain-level views inside that service.

## Domain dashboards

Create one dashboard for every business domain.

- Keep the overview row expanded.
- Collapse every detailed row by default.
- Show requests per second for externally facing domains.
- Show internal service calls per second when a domain has no external API.
- Show total calls, errors per second, error percentage, Top 10 errors, and p50/p95/p99 latency in
  the overview.
- Add one collapsed row for each important domain operation. Show its traffic, errors, and latency.
- Add a collapsed errors and logs row to every domain dashboard. Show errors per second plus warning
  and error logs.
- Link error panels to related traces and logs.

## Dependencies and runtime

Cover:

- PostgreSQL query rate, latency, failures, timeouts, transaction rollbacks, connection pool use,
  waiting callers, and pool exhaustion.
- S3 and storage request rate, latency, failures, timeouts, and retries.
- Keycloak, AI providers, and every outbound HTTP integration with call rate, latency, failures,
  timeouts, and retries.
- Background job starts, completions, failures, duration, retries, backlog, and oldest waiting job.
- JVM and process CPU, heap, garbage collection pauses, threads, restarts, and resource saturation.
- Telemetry export failures or missing telemetry so a broken monitoring pipeline is visible.

Enable Quarkus datasource metrics and JDBC tracing explicitly when required; they are not covered
by the existing HTTP server binder. Confirm AWS SDK and other client instrumentation from actual
emitted telemetry, then add manual instrumentation for missing signals.

## Alerts and verification

- Define service-level targets before choosing dashboard thresholds and colours.
- Alert on high error percentage, sustained 5xx responses, high p95 latency, dependency failure,
  database pool exhaustion, job backlog, and missing telemetry.
- Generate at least one success, expected rejection, unexpected 4xx, 5xx, dependency failure, and
  failed job where applicable.
- Confirm totals and Top 10 results change correctly across multiple Grafana time ranges.
- Confirm rates remain continuous across different dashboard resolutions.
- Confirm no PII or high-cardinality values appear in metric attributes or logs.
- Run the relevant tests and lint after changing instrumentation or dashboards.
