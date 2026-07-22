# Topics metrics

| Domain | Metric name | What the metric measures | User value |
| --- | --- | --- | --- |
| Topics — interests | `yoursay.topics.interest.changes` | Planned committed topic additions and removals by canonical topic, display group, action and onboarding/settings entry point. | Shows which subjects users want and how those preferences change over time. |
| Topics — interests | `yoursay.topics.interest.current` | Planned current unique-user count for each canonical topic and display group. | Shows the present audience for each subject so discovery can serve real interests. |
| Topics — interests | `yoursay.topics.interest.set.size` | Planned distribution of zero to seven selected interests by entry point. | Reveals whether onboarding makes it easy to express useful preferences, including skip rate. |
| Topics — interests | `yoursay.topics.interest.replace` | Planned interest-save attempts by entry point, outcome and bounded error code. | Identifies validation or service failures that stop users saving their interests. |
| Topics — interests | `yoursay.topics.interest.replace.duration` | Planned interest-save duration by entry point and outcome. | Keeps onboarding and settings changes responsive. |
| Topics — classification | Classification queue depth | Planned number of topic-classification jobs waiting to run. | Warns when users will wait longer for posts to become discoverable by topic. |
| Topics — classification | Oldest classification job age | Planned age of the oldest pending classification job. | Detects stuck work even when the queue itself is small. |
| Topics — classification | Classification job outcomes | Planned queued, succeeded, retried and terminal-failed totals by bounded classifier version. | Shows whether automatic topic assignment is dependable. |
| Topics — classification | Classification attempts | Planned number or distribution of attempts required per classification job. | Exposes repeated work that delays topic availability. |
| Topics — classification | Classification duration | Planned job duration by outcome and bounded classifier version. | Keeps automatic categorisation fast enough for timely publishing. |
| Topics — classification | Classification worker heartbeat | Planned liveness signal for the enabled classification worker. | Detects silent loss of topic processing before queues become stale. |
| Topics — classification | Classification output outcome | Planned count of classified and valid-unclassified posts by bounded classifier version. | Reveals when posts cannot be found through the topics users browse. |
| Topics — classification | Inferred assignments per post | Planned distribution of inferred topic-assignment counts per classified post. | Detects over- or under-classification that makes discovery noisy or sparse. |
| Topics — classification | Classification confidence | Planned aggregate confidence distribution by bounded classifier version. | Supports safe tuning of classification thresholds and topic quality. |
| Topics — classification | Topic assignment count | Planned assignment totals by canonical topic and assignment source. | Shows topic coverage and whether author, inference or admin input is shaping discovery. |
| Topics — classification | `yoursay.topics.classification.author.agreement` | Planned per-post exact, partial, none or no-author-topic agreement by bounded classifier version. | Shows whether automatic topics align with human intent without presenting authors as ground truth. |
| Topics — classification | `yoursay.topics.classification.author.topic.comparison` | Planned per-topic matched, author-only or inferred-only comparisons by canonical topic and classifier version. | Pinpoints subjects where automatic categorisation needs review. |
| Topics — classification | Author/inference Jaccard overlap | Planned distribution of intersection-over-union between author and inferred topic sets by classifier version. | Gives a graded view of classification alignment beyond all-or-nothing agreement. |
| Topics — browse | Topic browse requests | Planned browse request count by canonical topic. | Shows which topic pages users actually use. |
| Topics — browse | Topic browse empty results | Planned successful topic requests that return no posts, by canonical topic. | Reveals dead-end discovery experiences. |
| Topics — browse | Topic browse errors | Planned failed topic requests by canonical topic and bounded error code. | Identifies browsing failures that prevent users finding relevant stories. |
| Topics — browse | Topic browse latency | Planned topic-request duration by canonical topic and outcome. | Keeps category discovery responsive. |
| Topics — moderation | Pending topic reviews | Planned count of topic assignments awaiting admin review. | Prevents questionable categorisation from remaining unresolved. |
| Topics — moderation | Topic moderation actions | Planned verify, hide, restore and reclassify totals by canonical topic. | Shows whether moderation tools keep topic feeds trustworthy. |
| Topics — feed assembly | Topic batch lookup duration | Planned duration of batched topic-decoration lookups. | Detects slow assembly that delays feeds and post pages. |
| Topics — feed assembly | Missing topic assignments | Planned count of expected topic assignments missing during decoration. | Reveals incomplete post context before users receive partial results. |
| Topics — feed assembly | Topic assembly failures | Planned count of topic-decoration or feed-assembly failures. | Protects complete, reliable topic labels across product surfaces. |
