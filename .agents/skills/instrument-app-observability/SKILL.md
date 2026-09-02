---
name: instrument-app-observability
description: Plan, implement, and review Your Say News operation metrics, structured logs, traces, success/error/fault classification, and Grafana dashboards. Use before adding or changing production code, domains, APIs, internal service operations, background jobs, database or storage access, external integrations, or observability configuration so every important path has time-aware traffic, latency, and outcome coverage without exposing PII or creating high-cardinality telemetry.
---

# Instrument App Observability

Preserve complete observability whenever production behaviour changes. Instrument meaningful
boundaries and business operations, not trivial helpers or pure calculations.

## Workflow

1. Identify the owning domain and stable operation name for every changed execution path.
2. Check which HTTP, JVM, REST client, datasource, and trace signals Quarkus already emits before
   adding custom instrumentation.
3. Record exactly one terminal `success`, `error`, or `fault` outcome for each measured operation.
4. Add only the custom counters, timers, spans, and structured logs needed to cover domain work,
   internal calls, dependencies, jobs, and outcomes.
5. Add or update the overall and owning-domain Grafana dashboards in the same change.
6. Verify emitted telemetry and dashboard queries using successful, erroneous, and faulty
   operations.

## Operation outcomes

- `success`: the operation achieved its intended result.
- `error`: the operation did not succeed, but the application anticipated and deliberately handled
  that outcome. Examples include a wrong password mapped to `401`, rejected input mapped to `400`,
  or a requested resource that the contract maps to `404`.
- `fault`: the application, a dependency, or a background job failed unexpectedly or violated its
  contract. Examples include an unhandled exception, timeout, unavailable dependency, exhausted
  database pool, or the Unwrapped agent producing an invalid result.

Classify the operation outcome from its contract, not from whether an exception was thrown. An
intentionally thrown or mapped exception can be an `error`; an invalid result without an exception
can be a `fault`. Treat 4xx responses as errors unless the operation itself completed successfully
and the API contract proves otherwise. Treat 5xx responses as faults.

Do not count warnings as errors or faults unless the operation ended with that outcome. Group errors
by stable error code and faults by stable fault code or exception type. Never group by raw message
or stack trace.

## Metric and logging rules

- Measure traffic, success, errors, faults, and latency for every public API route.
- Measure calls, success, errors, faults, and latency for important internal domain operations,
  including operations that have no public API.
- Use a Prometheus counter or timer count for terminal operation outcomes. Every undertaken measured
  operation must contribute once, including handled errors and faults that do not produce an HTTP
  response.
- Use one shared operation-outcome metric schema across all domains, with `domain`, `operation`, and
  `outcome` labels, so Prometheus can aggregate the same metric per domain or across the whole app.
  Do not create only domain-specific metric names that make whole-app totals incomplete.
- Measure p50, p95, and p99 latency for APIs, important operations, and dependencies.
- Use consistent low-cardinality attributes: `domain`, `operation`, `outcome`, `error_type`,
  `error_code`, `fault_type`, `fault_code`, and `environment` where relevant.
- Restrict `outcome` to `success`, `error`, or `fault`. Use bounded `error_code` or `fault_code`
  values to explain non-success outcomes without changing the outcome taxonomy.
- Use stable route templates and operation names. Never use raw URLs containing identifiers.
- Never put user IDs, post IDs, vote IDs, request IDs, emails, tokens, PII, raw error messages, or
  other unbounded values in metric attributes.
- Write structured warning and error logs containing the domain, operation, outcome, stable error
  or fault code, and trace ID.
- Keep sensitive request data, credentials, voting data, and personal characteristics out of logs.
- Correlate metrics, logs, and traces so a dashboard error can lead to its trace and related logs.

## Metrics are the counting source

- Use Prometheus operation metrics for every Grafana success, error, and fault total, rate,
  percentage, and Top 10 calculation.
- Never calculate these values by counting Loki log lines. Logs are diagnostic evidence and may be
  sampled, duplicated, dropped, or written more than once for one operation.
- Calculate each domain's total errors from operation metrics filtered by that `domain` and
  `outcome="error"`.
- Calculate the whole-app total errors from the same metric without a domain filter. It must equal
  the sum of the domain error totals for the same dashboard filters and time range.
- Do not add separately instrumented HTTP and internal-operation totals unless the panel explicitly
  says it counts both kinds of undertaken operations.
- Show faults separately from errors. A combined non-success panel may sum `error` and `fault`, but
  it must be labelled as failures rather than errors.
- Keep Loki log panels beside the metric panels for investigation, and link metric panels to the
  relevant logs and traces.

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
- Total successful operations during the selected period.
- Total errors during the selected period.
- Total faults during the selected period.
- Errors and faults per second, with separate percentages.
- 4xx errors in a dedicated panel.
- 5xx faults in a dedicated panel.
- Top 10 errors during the selected period.
- Top 10 faults during the selected period.
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
- Show total calls, total successes, total errors, total faults, error and fault rates and
  percentages, Top 10 errors and faults, and p50/p95/p99 latency in the overview.
- Add one collapsed row for each important domain operation. Show its traffic, successes, errors,
  faults, and latency.
- Add a collapsed errors and logs row to every domain dashboard. Derive error and fault counts,
  rates, percentages, and Top 10 results from Prometheus. Use Loki only to show the related warning
  and error log entries.
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
- Alert separately on high error percentage and fault percentage, sustained 5xx responses, high p95
  latency, dependency faults, database pool exhaustion, job backlog, and missing telemetry.
- Generate at least one success, handled error, application fault, dependency fault, and failed job
  where applicable.
- Confirm whole-app and per-domain success, error, and fault totals and Top 10 results change
  correctly across multiple Grafana time ranges.
- Confirm rates remain continuous across different dashboard resolutions.
- Confirm no PII or high-cardinality values appear in metric attributes or logs.
- Run the relevant tests and lint after changing instrumentation or dashboards.
