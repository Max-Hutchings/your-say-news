---
name: access-hosted-grafana
description: Read metrics, logs and traces from the hosted dev environment in Grafana Cloud (stack bigcactus5). Use when checking whether dev telemetry is arriving, debugging the dev deploy, or answering questions about dev traffic, errors or latency.
---

# Access hosted Grafana (dev)

Dev telemetry flows: `post-service` -> Alloy (on the dev VM) -> Grafana Cloud stack `bigcactus5`
(`https://bigcactus5.grafana.net`). Local runs use the LGTM container instead, not this.

## Auth

Read-only (Viewer) service account token, stored in the macOS Keychain. Never print it.

```bash
T=$(security find-generic-password -a "$USER" -s ysn-grafana-token -w)   # read token from Keychain into a variable
G=https://bigcactus5.grafana.net/api/datasources/proxy/uid               # Grafana's datasource proxy base
```

If the Keychain entry is missing, ask the user for a Viewer token. Writes (dashboards, alerts) need
an Editor token from the user.

## Query

```bash
# Prometheus (metrics) - instant query
curl -s -H "Authorization: Bearer $T" --data-urlencode 'query=count by (job) (up)' "$G/grafanacloud-prom/api/v1/query"

# Loki (logs) - which services sent logs in the last hour (start in nanoseconds)
curl -s -H "Authorization: Bearer $T" "$G/grafanacloud-logs/loki/api/v1/label/service_name/values?start=$(( ($(date +%s)-3600)*1000000000 ))"

# Tempo (traces) - recent traces, or one by id
curl -s -H "Authorization: Bearer $T" "$G/grafanacloud-traces/api/search?limit=5&start=$(( $(date +%s)-900 ))&end=$(date +%s)"
curl -s -H "Authorization: Bearer $T" "$G/grafanacloud-traces/api/traces/<traceId>"
```

## Reading results

- Empty results everywhere, including `alloy_*` metrics = the Alloy -> Grafana Cloud leg is down.
  Check the Alloy container state in the `Deploy · Development` job log of the CI run
  (`gh run view <id> --log`).
- Alloy metrics present but no `post-service` data = the app -> Alloy leg is broken.
- Dashboards live in the "Your Say News" folder, sourced from `grafana/dashboards/`.
