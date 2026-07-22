# Post Unwrapped metrics

| Domain | Metric name | What the metric measures | User value |
| --- | --- | --- | --- |
| Post Unwrapped | Analysis outcomes by voting type | Planned analysis-generation success/failure split by bounded voting type and option count. | Ensures both binary and multiple-choice voters receive a useful explanation. |
| Post Unwrapped | Analysis milestone queue depth | Planned count of vote-milestone analysis jobs waiting to run. | Shows whether voters will receive analysis promptly. |
| Post Unwrapped | Analysis milestone queue age | Planned age of the oldest pending milestone-analysis job. | Detects stuck audience analysis before the experience becomes stale. |
| Post Unwrapped | Analysis cache hit rate | Planned share of analysis requests served from the versioned cache. | Reduces wait time and provider cost for voters. |
| Post Unwrapped | Analysis generation latency | Planned end-to-end aggregate-analysis duration by outcome and bounded model version. | Keeps the post-vote explanation timely. |
| Post Unwrapped | Analysis generation cost | Planned aggregate provider cost or bounded token usage per analysis generation. | Keeps repeated audience analysis sustainable. |
| Post Unwrapped | Analysis generation failures | Planned failed analysis generations by bounded reason and model version. | Reveals why voters do not receive Post Unwrapped. |
| Post Unwrapped | Analysis source count | Planned number of validated external sources used in each generated analysis. | Supports traceable context without overstating what audience data proves. |
| Post Unwrapped | Analysis quality evaluation | Planned offline evaluation of insight selection, sparse/noisy cases, unsupported-causation rejection, citation validity and human review. | Keeps audience explanations accurate, cautious and useful. |
| Post Unwrapped | Milestone processing concurrency | Planned active analysis-milestone jobs against the configured concurrency limit. | Prevents analysis work from overwhelming voting and feed journeys. |
