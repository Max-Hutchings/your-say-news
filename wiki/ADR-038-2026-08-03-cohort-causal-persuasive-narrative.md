# ADR-038 — Cohort causal persuasive narrative

Date: 2026-08-03

Absence handling amended by
[ADR-041](ADR-041-2026-08-03-option-complete-unwrapped-generation.md): when no cohort qualifies,
generation produces a general option argument with an explicit empty cohort list rather than
blocking the agent call.

## Situation

Post Unwrapped exists to explain why a statistically selected cohort is likely to have voted for
an option. The current implementation instead separates raw cohort identifiers, externally sourced
claims and a short synthesis into visually distinct blocks. Its prompt simultaneously asks for an
explanation of why a group voted a certain way and forbids causal explanation. The result is generic
pro/con copy using evasive phrases such as “may connect” or “may align”, rather than an interesting
account of the cohort's likely motivations.

When no cohort passes the deterministic narration rules, the current story can silently omit the
audience finding and fall back to a general argument about the option. That fallback does not fulfil
the purpose of Post Unwrapped and is difficult for a reviewer to distinguish from cohort analysis.

The publication proof also presents generated fields differently from the voter-facing experience.
Reviewers therefore cannot reliably judge the exact article that voters will read.

## Options considered

### 1. Keep observed data, wider context and synthesis as separate reader-facing sections

This makes the provenance of each field explicit, but produces a fragmented report rather than a
short persuasive article. It also makes the relationship between the sourced claims and the final
interpretation unclear.

### 2. Avoid explanations of why cohorts voted and report association only

This is statistically conservative, but removes the main value of Post Unwrapped. Raw percentages
already exist elsewhere in the product; Unwrapped is intended to offer a researched explanation of
the pattern.

### 3. Generate one short, cohort-led persuasive analysis for each option

Deterministic application code selects statistically eligible cohorts. The model then uses the
observed pattern and external research to argue why that cohort is likely to have voted that way.
The explanation is explicitly analysis rather than a directly observed statement of each voter's
private motivation.

## Decision

Choose option 3.

### Deterministic cohort selection

ADR-028's aggregate-only privacy boundary, statistical thresholds, multiple-comparison correction
and deterministic bounded shortlist remain in force. The model cannot introduce a cohort that the
application did not select.

The selected cohort statistics are application-owned facts. They must be supplied to generation and
rendered in human-readable language; raw cohort IDs are never voter-facing prose.

If an option has no statistically eligible cohort, the system must not generate a generic pro/con
substitute or invent an audience explanation. It reports insufficient demographic evidence and does
not present that option as a completed Unwrapped analysis.

### Explanatory inference is the product

For every selected cohort, the model should explain why that group is likely to have voted for the
option. It may connect the cohort's characteristic, circumstances, incentives, experiences or
values to the observed voting pattern and may use direct explanatory wording such as “likely
because”. This researched causal interpretation is the central purpose of the generated article.

The explanation must not be presented as a surveyed statement from every member of the cohort or as
proof of an individual's private motivation. This limitation does not prohibit a clear causal
hypothesis; it distinguishes a persuasive, evidence-informed explanation from a directly observed
fact.

Blanket bans on ordinary causal words such as `because`, `led`, `drove` and `chose` are removed.
Validation should instead ensure that the cohort is deterministically selected and that generated
prose does not falsely claim direct knowledge of an individual voter's reasoning.

### Reader-facing article

Each option receives one unified persuasive analysis, not separate “Observed here”, “Wider context”
and “Synthesis” sections. It consists of:

- one catchy, cohort-led headline; and
- two or three short paragraphs totalling 50–100 words.

The prose should naturally combine the observed cohort pattern, relevant external context and the
explanation of likely motivation. Citations remain available without breaking the analysis into
separate semantic blocks. A short standard sample limitation may remain outside the article body.

Headlines must identify the cohort and the insight. They must not use generic “agreement” or
“disagreement” constructions. Suitable patterns include:

- `Why <cohort> are most likely to <plain-language option>`;
- `<cohort> are most likely to <plain-language option>`; and
- `What makes <cohort> favour <plain-language option>`.

The headline should normally contain 6–10 words and must not merely restate the option label.

### Generation scope

This decision does not add new post input fields or new research/source gates. The existing
versioned aggregate, post context, web research and citation workflow remain the generation inputs.
It changes the narrative purpose, structure and wording of the output.

### Exact publication proof

The administrator must review the same reader-facing article presentation that voters will receive:
the same option label, headline, paragraphs, citation markers, limitation and source references.
The review surface must not flatten, omit, relabel or rearrange generated content. Shared rendering
or a shared presentation model should enforce this parity.

## Reason

Post Unwrapped should add understanding that a demographic chart cannot provide on its own. A
statistically disciplined cohort selection followed by a direct, researched account of likely
motivation preserves the reliable part of the pipeline without making the prose sterile.

A single 50–100-word article is easier to read and more persuasive than disconnected evidence and
synthesis blocks. Cohort-led headlines make the insight immediately clear. Refusing to generate a
generic fallback when no cohort qualifies keeps the product honest about whether it has something
meaningfully “unwrapped” to say.

An exact publication proof ensures that human approval applies to the experience users actually see.

## Consequences and follow-up work

- Replace the current page fields with a versioned article contract capable of preserving two or
  three paragraphs and their citations.
- Generate deterministic human-readable cohort/statistic context for the model and presentation.
- Rewrite the system and user prompts around researched causal explanation and remove the blanket
  causal-word prohibition.
- Add validation for a cohort-led 6–10-word headline and a 50–100-word, two-or-three-paragraph body.
- Return an insufficient-evidence state rather than a generic argument when an option has no
  eligible cohort.
- Render the admin proof and voter article from the same presentation contract.
- Add reviewed fixtures that fail on generic headlines, unsupported cohort introduction, fragmented
  prose, missing causal explanation, incorrect word/paragraph counts and admin/mobile divergence.
- This decision amends the wording boundary in
  [ADR-028](ADR-028-2026-07-25-safe-demographic-insight-selection.md) and the argument page model in
  [ADR-029](ADR-029-2026-07-25-versioned-unwrapped-story-lifecycle.md).
