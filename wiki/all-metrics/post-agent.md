# Post Agent metrics

| Metric | What it measures | User value |
| --- | --- | --- |
| Draft API traffic | Request outcome and duration for generation, reconnect, recovery and autosave routes. | Shows whether publishers can reach and recover Pepper drafts. |
| Generation outcome | Direct generation completions split into bounded `success` and `fault` outcomes. | Shows whether Pepper reliably produces a usable draft. |
| Generation latency | End-to-end provider and validation time for each bounded outcome. | Exposes slow or stuck generation without recording prompts. |
| Generation faults | Terminal failures split by bounded internal fault code. | Identifies provider, validation and server faults while users receive one safe message. |
| Source quality evaluation | Offline citation validity, source strength and claim coverage. | Helps editors publish grounded, traceable posts. |

Prometheus labels must remain bounded. Prompt text, user IDs, draft IDs, source URLs and provider
response IDs are excluded from metrics and structured logs.
