# Votes and aggregation metrics

| Domain | Metric name | What the metric measures | User value |
| --- | --- | --- | --- |
| Votes | Vote cast outcomes by voting type | Planned canonical vote success/failure split by bounded voting type and option count. | Ensures every supported vote format lets users have their say. |
| Votes — results | Results load outcomes by voting type | Planned results-load success/failure split by bounded voting type and option count. | Shows whether voters receive the promised aggregate feedback. |
| Votes — aggregation | Aggregate query duration | Planned duration of sentiment aggregation and snapshot queries by bounded operation and outcome. | Keeps results and analysis fast as vote volume grows. |
| Votes — aggregation | Aggregate query failures | Planned failed sentiment aggregation and snapshot queries by bounded operation and error code. | Detects failures that withhold aggregate feedback from voters. |
