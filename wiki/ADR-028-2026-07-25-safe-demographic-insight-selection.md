# ADR-028 — Safe demographic insight selection

Date: 2026-07-25

> **Amended by [ADR-038](ADR-038-2026-08-03-cohort-causal-persuasive-narrative.md):**
> deterministic statistical selection remains active, but the generated article may directly
> explain why a selected cohort is likely to have voted that way. The former ban on causal
> interpretation is superseded.

## Situation

Post Unwrapped must explain which demographic cohorts disproportionately selected each voting
option and research credible context for why that option may appeal to them. The product supports
dozens of characteristics, multi-select answers and selected two-characteristic intersections.
Searching that many cohorts creates three risks:

- a language model may cherry-pick the most dramatic percentage without accounting for sample size;
- small or overlapping cohorts may be narrated as if they were stable, exclusive populations; and
- repeated comparisons will produce chance differences that look meaningful.

The existing votes domain preserves the privacy boundary by freezing identity-free characteristic
snapshots on canonical votes and exposing aggregate counts and percentages only. Direct results
currently use configurable `suppressBelow=k`, with `k=0` selected for MVP1. Post Unwrapped needs
stricter narration rules even while the direct API continues to expose all aggregate buckets.

## Options considered

Every option below assumes the aggregate-only privacy boundary. The choice is how application code
narrows aggregate cohorts before LangChain4j researches and writes the story.

### 1. Give every aggregate bucket to the language model

The prompt would ask the model to find interesting cohorts and exercise caution.

This is the least application code, but selection would be non-deterministic, difficult to test and
vulnerable to sparse dramatic findings. A model should not decide whether an observed demographic
difference is statistically real.

### 2. Search a fixed set of core characteristics only

Always screen age, gender, political persuasion, geography, income, education and employment.

This is predictable, explainable and has a smaller multiple-comparison burden. It would also produce
repetitive stories, leave much of the characteristic dataset unused and miss topic-specific or
unexpected signals.

### 3. Send the dominant group from every characteristic

For each option and characteristic, select the bucket with the largest share of that option's
voters.

This is broad and simple, but “dominant” often reflects who voted on the post overall. If
working-age people are 70% of all voters and 70% of one option's voters, they are large but not
distinctive. Passing composition without propensity and over-index invites false explanations.

### 4. Send the strongest safe over-index across every reportable field

Apply deterministic statistical gates to all reportable cohorts and send the largest positive
differences for each option.

This finds surprising patterns and is safer than raw dominance. It still spends statistical power
searching semantically irrelevant fields and can elevate technically significant but editorially
useless findings such as eye colour.

### 5. Search topic-selected characteristics

Choose relevant characteristics from a governed topic-to-axis mapping before examining outcomes.
Tax could admit income and employment; transport could admit region and urban/rural context.

This produces plausible, researchable explanations and reduces the comparison family. It relies on
a maintained mapping, may miss genuine surprises and cannot be the only initial strategy while
canonical topics remain unfinished.

### 6. Produce a bounded hybrid shortlist

Combine a stable core screen with topic-relevant axes and one controlled discovery/intersection
slot. Application code sends at most four statistically eligible candidates per option. The model
may choose no more than two based on the strength of available sourced context.

This retains predictable core coverage, allows relevant and surprising findings, bounds prompt
size and prevents the model from rationalising every characteristic. It requires more deterministic
selection logic and a governed characteristic-tier catalogue.

## Decision

Choose option 6.

### Aggregate-only boundary

The `votes` domain owns all aggregation and statistical calculations. It exposes a versioned
`PostAnalysisAggregate` contract through a public top-level interface. The contract may contain:

- post and immutable option context;
- overall and cohort option counts and percentages;
- aggregate sample sizes;
- differences from overall and from everyone outside the cohort;
- confidence intervals, raw p-values and adjusted q-values;
- suppression, rule-set and aggregate-version metadata.

It must never contain a voter ID, email, individual vote row, or identity-characteristic pairing.
The Unwrapped domain may not import vote repositories or vote entities.

The complete snapshot is produced from one consistent database view and persisted before external
research begins. A hash of its canonical JSON is the aggregate version used for audit and caching.
Capture runs in a repeatable-read transaction. The canonical hash excludes delivery metadata such
as `capturedAt` and the hash field itself, so unchanged post/vote data always produces the same
aggregate version while the returned snapshot still records when it was captured.

### Direct-result suppression and agent narration

Keep the MVP direct-results configuration at:

```text
votes.aggregation.suppress-below=0
```

`k=0` is not permission for Pepper to narrate tiny cohorts. Post Unwrapped applies independent,
stricter narration gates. Initial versioned defaults are:

| Rule | Initial value |
| --- | ---: |
| Minimum overall sample for observed demographic analysis | 100 |
| Minimum single-axis cohort | 30 |
| Minimum two-axis intersection | 40 |
| Minimum cohort share | 5% |
| Minimum absolute effect | 10 percentage points |
| Confidence interval | 95% Wilson |
| Multiple-comparison correction | Benjamini–Hochberg |
| Maximum false-discovery rate | `q <= 0.05` |
| Maximum selected insights per option | 2 |

Significance compares the cohort with everyone outside it, not with an overall percentage that
already contains the cohort. Use Fisher's exact test for small expected cells and a two-proportion
test otherwise.

These values are configuration carried in a named rule-set version. Changing them creates a new
analysis version; it does not silently reinterpret a stored story.

### Bounded hybrid shortlist

The aggregate dataset and the narration shortlist have deliberately different scopes.

The votes domain aggregates every retained, reportable non-news characteristic as a single-axis
cohort. This includes sex at birth, sexual orientation, marital status, race, UK county, country of
birth, citizenship, religion, religiosity, university subject, height, weight, eye colour,
parenthood, pet ownership/type, chronotype, outlook, neurodivergence/type, disability/type and
housing/property, in addition to the existing core axes.

Aggregation does not by itself authorize a characteristic for generated prose. Characteristic axes
retain three narration tiers:

| Tier | Characteristics | Treatment |
| --- | --- | --- |
| Core | age, gender, political persuasion, country/region, urban-rural, personal/household income tier, education, occupation and employment sector | Always eligible for deterministic screening |
| Topic-conditional or sensitive | housing/property, parenthood, citizenship/birthplace, religion/religiosity, disability, neurodivergence, sexual orientation, race and sex at birth | Screen only through reviewed topic/privacy allowlists |
| Excluded from agent v1 | height, weight, eye colour, pets, chronotype and outlook | May remain in direct diagrams but cannot drive persuasive Unwrapped prose |

Until governed canonical topics and their axis mapping exist, the topic-conditional slot remains
empty. The model must not infer a topic and thereby grant itself access to another characteristic.

For every voting option, deterministic code:

1. creates eligible core, topic-allowed and allowlisted intersection aggregates;
2. applies narration sample and cohort-share floors;
3. calculates composition, propensity, over-index, effect size and uncertainty;
4. corrects the complete searched comparison family;
5. removes redundant or nested findings; and
6. fills, where available, no more than four candidate roles:
   - one broad core anchor with high coverage and positive over-representation;
   - one strongest safe core differentiator;
   - one strongest safe topic-relevant candidate; and
   - one non-redundant allowlisted intersection or discovery candidate.

Use deterministic ranking and tie-breakers within each role. A candidate carries its role,
relevance reason, composition, propensity, over-index, sample, interval and adjusted q-value.

LangChain4j receives the bounded shortlist and may use no more than two candidates on an option
page. It may omit a candidate when credible contextual research is unavailable. It may never
introduce a cohort that application code did not shortlist.

If no cohort passes, the option page must say there is no reliable demographic concentration yet.
The model may still research the wider factual case for that option, but may not invent an audience
pattern.

### Intersections

Intersections contain at most two dimensions and come from a reviewed allowlist. Do not generate
arbitrary combinations. The initial candidates may include age × gender, age × occupation,
age × employment sector, income × gender and political persuasion × income.

Sensitive pairings require explicit privacy/product review. Multi-select axes are excluded from
intersections in the first version.

### Multi-select characteristics

Do not collapse multi-select answers into synthetic labels such as `ASIAN+WHITE`. Use
`MULTI_MEMBERSHIP` semantics:

- a voter contributes once to every value they selected;
- each bucket's denominator is the number of voters selecting that value;
- bucket populations overlap and their totals need not equal the overall total; and
- neither the UI nor agent may add overlapping buckets together or describe them as exclusive
  population composition.

Exclusive axes declare `EXCLUSIVE` semantics.

### News-habit exclusion

News habits are explicitly outside this aggregation expansion. Do not add `newsFrequency`,
`balancedNewsViewpoint`, `mainstreamNewsPercent` or `betterWorldWithData` to the post-analysis
cohort family in this change.

`newsFrequency` remains available on existing vote snapshots. Work already in progress may retain
the newly snapshotted `balancedNewsViewpoint` and bucketed `mainstreamNewsPercent`, but neither is
aggregated for Post Unwrapped. `betterWorldWithData` is not added to vote snapshots. Adding any of
these four axes later requires governed reporting semantics and an explicit ADR update.

### Wording boundary

Every generated claim distinguishes:

1. **Observed:** what aggregate Your Say votes show;
2. **Context:** facts from cited external sources; and
3. **Interpretation:** a cautious hypothesis.

Use wording such as “among people who voted on this post” and “may be connected to”. Do not claim
that a demographic characteristic caused a vote or that this self-selected audience represents a
country's population.

### UI presentation boundary

Mobile and administrator UIs must not render a generic notice or caveat saying that an analysis or
association describes only people who voted on the post, is not a population survey, or does not
represent a broader population. Repeating those standalone disclaimers is prohibited product copy.

This presentation rule does not relax the aggregate-only boundary, statistical eligibility gates,
or ban on population claims. Existing `notice` and `caveat` transport fields may remain for schema
compatibility, but they are not presentational fields and must not be displayed.

## Reason

The product needs persuasive explanation without sacrificing statistical or privacy integrity.
Deterministic selection makes the same aggregate input produce the same candidate insights, gives
tests exact expected outcomes and prevents a model from treating noise as discovery.

Keeping statistical work in `votes` preserves the aggregate-only boundary. Keeping narration floors
separate from `k=0` allows the agreed direct result policy without turning unsafe or unstable small
cohorts into prominent prose.

The bounded hybrid is more varied than a core-only policy without the base-rate error of sending
every dominant group. It is more relevant than exhaustive over-indexing and still reserves a
controlled path for a surprising intersection. Giving the model several already-safe candidates
lets research quality influence the final narrative without letting the model decide statistical
validity.

## Consequences and follow-up work

- Extend the aggregate DTOs with per-option effects, uncertainty, membership semantics and
  versioned rule metadata.
- Aggregate every retained non-news characteristic while keeping the bounded selector as the
  independent narration gate.
- Revisit the four excluded news-habit answers only after their reporting semantics are governed.
- Replace joined multi-select labels with overlapping membership aggregates.
- Build a small reviewed intersection allowlist.
- Add the versioned characteristic-tier and topic-to-axis catalogues.
- Add explicit candidate-role, composition, propensity, over-index and relevance metadata to the
  Unwrapped input contract.
- Add deterministic test fixtures for strong, sparse, noisy, redundant and no-signal scenarios.
- Prove the selector rejects dramatic small cohorts, honours the four-role budget and pins exact
  shortlisted cohort IDs/statistics.
- Keep generic voter-sample and population-representativeness disclaimers out of voter and
  administrator UI rendering, including values retained in compatibility fields.
- `k=0` remains a known direct-results privacy risk and must be revisited before release.
- Any change to thresholds, intersection policy or inference language requires a new rule-set
  version and an ADR update.
- The full workflow and story lifecycle are defined by
  [ADR-029](ADR-029-2026-07-25-versioned-unwrapped-story-lifecycle.md).
- The detailed implementation plan is
  [Post Unwrapped agent architecture](../docs/plans/post-unwrapped-agent-architecture.md).
