# ADR-041 — Option-complete Unwrapped generation without a demographic shortlist

Date: 2026-08-03

## Situation

Normal administrator generation and direct prompt benchmarking both generate a researched argument
for every voting option. The deterministic selector may have no statistically eligible demographic
cohort for an option even though the post has enough votes to generate a useful option-level
argument. Blocking before the agent call makes direct benchmarking behave differently from the
existing option-complete generation flow.

## Decision

Generation remains option-complete when an option has no demographic shortlist.

- Aggregate capture and deterministic cohort selection still run before every normal or benchmark
  agent call.
- When candidates exist, the model must select only supplied cohort IDs and produce cohort-led copy.
- When candidates do not exist, the model must return an empty `selectedCohortIds` list, write a
  general researched argument for that voting option, and must not invent demographic evidence.
- A benchmark lane invokes the same generator directly with a custom system message. It skips
  reconciliation, jobs, scheduling, persistence and review.
- One agent call still returns one argument page for every ordered voting option.

## Reason

The availability of a safe demographic observation and the availability of a researched argument
for a voting option are different questions. Preserving an explicit empty cohort list keeps that
distinction machine-verifiable while allowing normal generation and prompt benchmarking to retain
the same option-complete behaviour.

## Consequences

- Review and reader presentation can distinguish cohort-led pages from general option pages using
  `selectedCohortIds` without exposing private vote data.
- Validation continues to reject invented cohort IDs and requires a cohort-led headline whenever a
  shortlist was supplied.
- This decision supersedes ADR-038 only where no statistically eligible cohort exists. Its cohort
  selection, causal wording, article structure and citation rules remain in force otherwise.
