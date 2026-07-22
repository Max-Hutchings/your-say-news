# Post Agent metrics

| Domain | Metric name | What the metric measures | User value |
| --- | --- | --- | --- |
| Post Agent | Agent run outcomes | Planned generation-job run count by outcome and bounded model/prompt version. | Shows whether publishers can reliably create assisted drafts. |
| Post Agent | Generation cost | Planned aggregate provider cost or bounded token usage per generation run. | Keeps assisted publishing economically sustainable. |
| Post Agent | Generation latency | Planned end-to-end draft-generation duration by outcome and bounded model version. | Sets honest wait times and prevents the creation flow feeling stuck. |
| Post Agent | Generation retries | Planned retry count per generation job and bounded failure class. | Exposes transient instability that delays publishers. |
| Post Agent | Generation source count | Planned number of validated sources returned per generated draft. | Helps ensure users receive grounded, traceable reporting. |
| Post Agent | Generation failures by reason | Planned terminal generation failures by bounded reason and model version. | Pinpoints why publishers cannot complete assisted creation. |
| Post Agent | Draft balance evaluation | Planned offline evaluation of representative drafts for one-sided framing, false balance and unsupported certainty. | Protects users from misleading claims that assisted reporting is unbiased. |
| Post Agent | Source quality evaluation | Planned offline evaluation of citation validity, source strength and claim coverage. | Helps users verify claims against trustworthy evidence. |
