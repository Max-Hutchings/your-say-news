# Platform and cross-domain backend metrics

| Domain | Metric name | What the metric measures | User value |
| --- | --- | --- | --- |
| Platform | HTTP request rate | Current dashboard measure derived from the Quarkus HTTP-server request-duration count, split by service and route. | Shows traffic and demand changes so user journeys remain available under load. |
| Platform | HTTP 5xx error ratio | Current dashboard ratio of server-error responses to all HTTP responses, split by service. | Reveals service failures that prevent users from completing journeys. |
| Platform | HTTP latency percentiles | Current dashboard p50, p95 and p99 request duration, split by service or route. | Identifies slow journeys that make the product feel unresponsive. |
| Platform | JVM CPU utilisation | Current dashboard process/system CPU utilisation for each backend service. | Warns when resource pressure could slow or interrupt the product. |
| Platform | JVM memory used | Current dashboard heap and JVM memory consumption for each backend service. | Exposes memory pressure before it causes pauses or outages. |
| Platform | JVM live thread count | Current dashboard count of live JVM threads per backend service. | Helps detect resource exhaustion that could block requests. |
| Platform | JVM loaded class count | Current dashboard count of classes loaded in each backend service. | Helps diagnose abnormal runtime growth that may threaten stability. |
| Backend — all domains | `yoursay.domain.requests.total` | Emitted HTTP request count, split by domain, operation, status and outcome. | Reveals changes and failures in user-facing journeys so the team can respond before they become widespread. |
| Backend — all domains | `yoursay.domain.throughput.total` | Emitted HTTP throughput count, currently incremented once for every recorded request with the same dimensions as request total. | Provides an explicit traffic series for capacity and demand monitoring. |
| Backend — all domains | `yoursay.domain.request.duration` | Emitted HTTP request-duration timer, split by domain, operation, status and outcome. | Identifies slow domain operations behind a poor user experience. |
| Backend — instrumented domains | `yoursay.domain.operations.total` | Emitted business-operation attempts, split by domain, operation and outcome. | Shows whether the product is completing the actions users ask it to perform. |
| Backend — instrumented domains | `yoursay.domain.success.total` | Emitted successful business-operation count, split by domain and operation. | Makes falling success rates visible before they erode user trust. |
| Backend — all domains | `yoursay.domain.errors.total` | Emitted failed HTTP requests and failed instrumented business operations, split by bounded domain and operation tags. | Highlights unreliable journeys so the most harmful failures can be prioritised. |
| Backend — API errors | `yoursay.domain.errors.by_code.total` | Emitted API error count, split by domain, operation, bounded error code and HTTP status. | Distinguishes user-correctable errors from service faults that block users. |
