# ADR-047 - Per-domain dashboard set

Date: 2026-08-13

## Situation

`instrument-app-observability` requires one overall dashboard plus one dashboard for every business
domain, each with an expanded overview row, collapsed detail rows per important operation, and a
collapsed errors-and-logs row linking to traces. What was provisioned did not match.

The five dashboards in `grafana/dashboards/` were:

- `your-say-news-overview.json` and `your-say-news-service-detail.json` - built before the service
  split was resolved. Both filtered on `job=~"user-service|post-service"`, and `user-service` no
  longer exists. Both queried only raw `http_server_*` and JVM series, so no panel used the
  `yoursay_domain_*` metrics the application actually emits. They had no error total, no error
  percentage, no dedicated 5xx or unexpected-4xx panel, no expected-rejection panel and no Top 10
  errors.
- `your-say-news-logs-traces.json` - a standalone log and trace viewer, which the skill instead
  wants as a row inside each domain dashboard so an error leads to its own logs and traces.
- `unwrapped-generation.json` and `user-characteristics-income-profiles.json` - real domain
  dashboards, but only two domains had one, and the income-profiles one was scoped to a single
  feature rather than to the `user` domain.

Six domains had no dashboard at all: feed, posts, topics, votes, user and social.

## Options considered

1. **Add the six missing dashboards, leave the existing five alone.** Cheapest. Keeps a stale
   overview that reports on a service that does not exist, and leaves two different dashboard
   shapes in the folder.
2. **One dashboard per domain, generated from a template, replacing the whole folder.** Every domain
   view is structurally identical; the overview is rebuilt on the metrics the app emits;
   logs-traces and service-detail fold into the overview and the per-domain error rows.
3. **A single dashboard with a `domain` variable and repeated rows.** One artifact to maintain, but
   domain-specific detail - the AI provider, the S3 presign path, the job queues - cannot be
   expressed in a repeated row, and it collapses to the lowest common denominator.

## Decision

Option 2. `grafana/dashboards/` now holds exactly seven dashboards:

| File | UID | Covers |
| --- | --- | --- |
| `your-say-news-overview.json` | `ysn-overview` | Whole service, all domains |
| `domain-feed.json` | `ysn-domain-feed` | `feed` |
| `domain-posts.json` | `ysn-domain-posts` | `posts`, `postagent` |
| `domain-topics.json` | `ysn-domain-topics` | `topics` |
| `domain-unwrapped.json` | `ysn-domain-unwrapped` | `unwrapped` |
| `domain-user.json` | `ysn-domain-user` | `user`, `usercharacteristic`, `social` |
| `domain-votes.json` | `ysn-domain-votes` | `votes` |

Every domain dashboard has the same shape: an expanded **Overview** row (traffic, calls in range,
errors, errors per second, error percentage, traffic by operation and outcome, p50/p95/p99, Top 10
failing operations, Top 10 error codes), one collapsed row per important operation, and a collapsed
**Errors and logs** row. Domains with a dependency or a job queue get an extra collapsed row for it.

`domain-user.json` spans three domain labels because `user`, `usercharacteristic` and `social` are
subdomains of one product area; splitting them would have produced three near-empty dashboards.
`domain-posts.json` includes `postagent` for the same reason.

Service level targets, which set every threshold colour:

| Signal | Green | Yellow | Red |
| --- | --- | --- | --- |
| Error percentage | < 1% | 1-5% | > 5% |
| API and internal operation p95 | < 300ms | 300ms-1s | > 1s |
| Background job p95 | < 60s | 60-300s | > 300s |
| AI research provider p95 | < 30s | 30-120s | > 120s |
| Database connection wait | < 50ms | 50-250ms | > 250ms |
| GC pause per second | < 50ms | 50-200ms | > 200ms |

Latency panels are in milliseconds because that is the unit the histograms are emitted in.

## Reason

- The dashboards now read the metrics the application emits. `DomainMetrics` already tags every
  counter with `domain`, `operation`, `outcome`, `error_type`, `error_code` and `environment`; the
  old dashboards ignored all of it.
- Identical structure means a domain view is readable without learning it. Anyone who can read the
  votes dashboard can read the topics one.
- Expected rejections get their own panels everywhere. A duplicate vote and a locked results page
  are contract-defined refusals; counting them as errors would have made the error rate meaningless
  and hidden real faults.
- Every error panel links into Explore with the dashboard's own time range, so a spike leads to the
  traces and logs that produced it rather than to a manual search.
- Thresholds come from the targets above rather than from whatever looked reasonable per panel.

## What building this against a live stack exposed

The queries were written first from the metric names used by the two dashboards shipped in `043a120`
and `b10a75a`, then checked against a running stack. Four of those assumptions were wrong, and the
two earlier dashboards carry the same mistakes:

1. **Latency histograms are milliseconds, not seconds.** The emitted names are
   `yoursay_domain_request_duration_milliseconds_bucket` and
   `yoursay_domain_operation_duration_milliseconds_bucket`. Every previous latency panel queried
   `..._seconds_bucket` and therefore charted nothing at all.
2. **The connection pool is not Agroal.** `post-service` runs Hibernate Reactive, so the pool is the
   Vert.x SQL client (`sql_pool_active`, `sql_pool_idle`, `sql_pool_queue_size`,
   `sql_pool_queue_delay_milliseconds_*`). No `agroal_*` series exists.
3. **There is no outbound HTTP client instrumentation.** No `http_client_*` metric exists, so
   Keycloak and S3 calls cannot be charted. The AI provider is the exception and is well covered by
   `gen_ai_client_*` and `langchain4j_aiservices_*`, including token usage by type.
4. **Operation names contained a stray empty segment** - `GET..feed` rather than `GET.feed` - because
   `operationFrom` turned the path's leading slash into an empty segment.
5. **The operation tag was unbounded**, which is the serious one. `operationFrom` recognised
   identifiers by *shape* - UUID, all-digits, contains-`@`, or the segment after `topic-tags` - and
   kept every other segment verbatim. Three ways in:
   - `/votes/{postId}/sentiment/{axis}` takes a free-form `axis`. The whitelist check runs inside the
     controller, *after* the response filter has read the raw URI, so any string a caller invented
     became a tag value on four meters.
   - Any probe of an unmapped path, such as `/wp-admin/setup-config.php`, minted a new tag value.
   - `/your-say-user/email/{email}` only collapsed when the segment contained `@`, so a malformed
     address stayed verbatim on the one route most likely to carry a real identifier.

   `operationFrom` now works from an **allowlist of the literal segments that appear in this
   service's routes**; anything else becomes `{id}`. Collapsing by default means a forgotten literal
   degrades to a placeholder rather than to an unbounded metric, which is the failure direction we
   want. Recognising identifiers by shape can only ever be a denylist, and a denylist on a metric tag
   is a cardinality bomb waiting for the next route.

Two further defects were visible in the live data rather than in the code:

- **No request was ever classified as an expected rejection.** `DomainRequestFilter` called the
  four-argument `recordRequest`, which hard-codes `expectedRejection = false`. Locking sentiment
  results until the caller votes showed up as `unexpected_client_error`, so the feature working as
  designed was counted as a fault, and the skill's expected-rejection panels could never populate.
- **Every API refusal was logged at ERROR with a stack trace**, including those same
  contract-defined rejections.

## Consequences and follow-up work

Code changed alongside the dashboards:

- `ApiException` gained `expectedRejection()`. `VoteApiException.duplicateVote` (one vote per user
  per post) and `resultsLocked` (results stay locked until you vote) are the two contract-defined
  rejections today. `ApiExceptionMapper` passes the verdict to the response filter through a request
  property, excludes them from the error counter, and logs them as a structured warning with no
  stack trace. Status code alone must never decide this: two 403s from one domain can mean
  different things.
- `DomainRequestFilter.operationFrom` strips the leading slash and collapses every segment outside
  `ROUTE_LITERALS`, so operation names are now `GET.feed`, `GET.posts.{id}`, `POST.votes`,
  `GET.votes.{id}.sentiment.{id}`. **Adding a route means adding its literal segments to that set**;
  forgetting to is safe but makes the operation name less specific.
- `quarkus.hibernate-orm.metrics.enabled=true` was added, giving statement counts and
  committed-versus-rolled-back transactions. The pool metrics only describe connections.

Standing constraints and gaps:

- No panel groups by a user id, post id, vote id, email or raw error message. Top 10 panels group by
  `error_code` and `operation` only. This is a constraint on future panels, not just current ones.
- **Requests rejected by authentication never reach the JAX-RS filter**, so a 401 is invisible to
  every domain metric. This was confirmed by sending unauthenticated traffic and seeing no series
  appear. Covering it needs instrumentation at the HTTP layer rather than a dashboard change.
- Keycloak and S3 have no dependency metrics at all - see point 3 above. Both currently produce
  traces only, so the dashboards link to Tempo instead of charting them.
- Postgres query latency and timeouts likewise live in spans rather than metrics, because JDBC
  telemetry emits traces.
- Log panels match on log text through a `logFilter` dashboard variable rather than a structured
  `domain` label. Making every domain emit `domain=...` in its warning and error logs would let the
  panels use a label matcher instead.
- Alerts are not defined yet. The targets in this ADR are the thresholds they should use.
- Every panel query except one was checked against live data. `yoursay_domain_job_duration_-
  milliseconds_bucket` could not be confirmed because no background job ran during the window; it is
  registered by the same `Timer.builder` path as the two verified histograms.
