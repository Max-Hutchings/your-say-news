# Post Unwrapped system-prompt benchmarking

## Goal

Give administrators a three-way comparison workspace for one post. Each lane accepts a complete
replacement system prompt and returns a generated Post Unwrapped draft beside the other lanes.

## Product rules

- Keep the existing `Run analysis` action and durable review workflow unchanged.
- Add `Generate benchmarking` beside it for every post eligible for Unwrapped generation.
- Open a dedicated, reversible page-level view containing the selected post, aggregate vote split,
  three prompt editors and three result lanes.
- Seed each editor with the production Unwrapped system prompt so an administrator can make focused
  variants without copying it from source code.
- Keep up to three prompt lanes and let an administrator generate any lane independently.
- A successful rerun replaces only its own lane. Other results, and the previous result during a
  rerun or failed attempt, remain visible for comparison.
- Accept one to three non-blank prompts in the administrator-only API contract. The page sends one
  prompt per lane action.
- Benchmark runs are direct and ephemeral: they do not create reconciliation markers, jobs, stories
  or review-queue entries.
- Each lane makes one direct agent call for the full post and returns every voting option. A
  model-correctable format failure may trigger up to four bounded repair calls; the response and UI
  must expose the attempt count and exact effective system prompt. An option without a qualifying
  demographic shortlist remains generatable with an explicit empty cohort list; it must not invent
  demographic evidence.
- Capture the aggregate and deterministic cohort selection once per API request. Reuse that request
  when multiple prompts are supplied, while the page normally submits one independent lane. Keep
  the normal user-message output contract, web research, citation extraction and draft validation
  unchanged.
- Isolate lane failures so a malformed provider response in one lane does not hide successful
  comparisons from the other two.

## Backend

1. Move the production system prompt into one internal constant and use it for both normal
   generation and benchmark editor defaults.
2. Add a custom-system-prompt generator entry point while retaining the existing normal entry point.
3. Extract aggregate-to-research-request preparation so the scheduled worker and direct benchmark
   runner share the same path.
4. Add administrator endpoints to read the current default prompt and synchronously generate one to
   three ephemeral benchmark results.
5. Return publication-shaped argument pages, provider metadata, repair provenance and a safe
   per-lane error when a lane fails.

## Admin UI

The subject is an internal editorial experiment bench. Its single job is to make prompt differences
easy to scan without losing the fixed post context.

- Reuse the existing paper/ink/lime editorial tokens and Newsreader, Schibsted Grotesk and Spline
  Sans Mono typography.
- Use a full-width post dossier above a three-column comparison rail.
- Make the three prompt editors the page's signature element: labelled vertical strips run from
  prompt input into their corresponding output, making prompt-to-result provenance obvious.
- Show the provider attempt count and disclose the exact effective repair prompt whenever a lane
  required format repair.
- Keep motion restrained to result loading states and preserve visible focus and narrow-screen
  horizontal scrolling rather than crushing three comparisons into unreadable columns.

```text
back  POST QUESTION                                      vote split
      summary · jurisdiction · total

      PROMPT A              PROMPT B              PROMPT C
      [textarea]            [textarea]            [textarea]
      [Generate A]          [Generate B]          [Generate C]

      RESULT A              RESULT B              RESULT C
      article pages         article pages         article pages
```

## Verification

- Generator unit tests prove the normal path uses the production prompt and the override path sends
  the exact custom prompt while preserving the same user-message contract and validation.
- Benchmark-runner unit tests prove a single prepared request is reused within a request, prompts
  stay ordered, bounded format repairs expose their exact effective prompt and one failure is isolated.
- Controller integration tests prove admin-only access, input validation and absence of persistent
  jobs, stories and reconciliation markers.
- Frontend API and component tests pin request payloads, navigation, editor defaults, loading/error
  states and side-by-side result rendering.
