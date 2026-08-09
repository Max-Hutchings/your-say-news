# Unwrapped agent — prompt hygiene plan

## Situation

`LangChain4jUnwrappedResearchGenerator.researchPrompt` builds a user message containing a 20-line
`OUTPUT CONTRACT` followed by `INPUT JSON`. `UnwrappedSystemPrompt.DEFAULT` holds editorial style
guidance. The roles are inverted: the user message should carry only the data for the task, and
every stable instruction should live in the system message.

Auditing that boundary surfaces five further problems in the same area. Ordered by value.

---

## 1. Move the output contract into the system message (the trigger)

**Change.** `UnwrappedSystemPrompt` gains a `CONTRACT` block holding everything currently under
`OUTPUT CONTRACT`. `researchPrompt` reduces to the serialised request. The contract stops naming a
count and an id list ("Return exactly %d pages … in this exact optionId order: %s") and instead
says "return exactly one page per supplied option, in the order the options appear in the input" —
the count and the ordering are data, and the data already carries them.

**Why.** Instructions belong to the role that is stable across calls. It also lengthens the stable
prefix behind `quarkus.langchain4j.openai.unwrapped.chat-model.responses.prompt-cache-key`
(`post-unwrapped-v2`): today the ~350 cacheable tokens of contract sit *after* the varying part
starts, so they are re-billed every run. After the move the only varying content is the JSON.

**Files.** `agent/UnwrappedSystemPrompt.java`, `agent/LangChain4jUnwrappedResearchGenerator.java`.

## 2. Split the system message into fixed contract + editable guidance

**Problem this creates if skipped.** Benchmark lanes replace the *entire* system prompt
(`UnwrappedBenchmarkPage.tsx:176`, `UnwrappedResearchAiService.researchWithSystemPrompt`). Once the
contract lives in the system message, any lane that omits it fails validation on every attempt, and
lanes end up re-litigating the contract instead of comparing persuasive style.

**Change.** Two segments, always sent in order:

- `UnwrappedSystemPrompt.CONTRACT` — immutable, never benchmarked.
- `UnwrappedSystemPrompt.DEFAULT_GUIDANCE` — editorial voice, cohort reasoning, statistical
  honesty; this is what a benchmark lane replaces.

`UnwrappedResearchAiService` keeps one method: `research(@V("guidance") String guidance,
@UserMessage String brief)` with `@SystemMessage("{{contract}}\n\n{{guidance}}")`. Production
passes `DEFAULT_GUIDANCE`; benchmark passes the lane text. The two-method split disappears.
`GET /prompt` (the endpoint the admin page loads) returns the guidance only, and the page copy
changes to "Each editor replaces the guidance section; the output contract is fixed."

**Files.** `agent/UnwrappedSystemPrompt.java`, `agent/UnwrappedResearchAiService.java`,
`agent/LangChain4jUnwrappedResearchGenerator.java`, `UnwrappedAdminController`/`UnwrappedService`,
`webui/.../UnwrappedBenchmarkPage.tsx` + test.

## 3. Send repair feedback as a user turn, not by mutating the system prompt

`UnwrappedBenchmarkRunner.repairPrompt` concatenates the validation failure onto the system prompt
(`:98-110`). That is conversational feedback about one attempt, so it is user data; appending it to
the system message also changes the cache prefix on every retry, discarding the prompt cache
exactly when we are retrying and paying most.

**Change.** Add a `String repairNote` (nullable) to `UnwrappedResearchRequest`'s call path — pass
it as a second user message, or as a trailing `PREVIOUS ATTEMPT FAILED:` section of the user
message after the JSON. Guidance stays untouched across attempts, so
`UnwrappedBenchmarkVariantDto.effectivePrompt` becomes "guidance + repair note" rather than a
mutated prompt.

**Also flag:** the production path (`UnwrappedGenerationWorker:46`) has no repair loop at all —
only benchmark retries formatting. Decide deliberately whether production should share
`MAX_FORMAT_ATTEMPTS`, and record it in the ADR.

## 4. Stop asking the model for the caveat

`UnwrappedArgumentDraftV1.caveat` must match `UnwrappedDraftValidator.REQUIRED_CAVEAT` byte for
byte. We spend output tokens generating a constant, repeat it in three prompts, and carry a whole
retryable failure class (`UNWRAPPED_OBSERVED_CAVEAT`) for the times the model paraphrases it.

**Change.** Drop `caveat` from the model-facing draft record; `UnwrappedStoryResponseAssembler`
injects the constant. Delete the caveat lines from the contract and the repair prompt, and remove
`UNWRAPPED_OBSERVED_CAVEAT` from `RETRYABLE_FORMAT_CODES`.

**Files.** `dto/UnwrappedArgumentDraftV1.java`, `validation/UnwrappedDraftValidator.java`,
`service/UnwrappedStoryResponseAssembler.java`, `service/UnwrappedBenchmarkRunner.java`,
`agent/LangChain4jUnwrappedResearchGenerator` (stub result).

## 5. Give the prompt its own input DTO instead of serialising the internal request

`objectMapper.writeValueAsString(request)` dumps `UnwrappedResearchRequest` verbatim, which means:

- **Instructions hide inside the data.** `OptionBriefV1.narrativeInstructions` and
  `insufficientEvidence` are prose directives arriving as JSON fields — the same role confusion
  this plan is fixing, one level down. Either fold them into the contract or render them as an
  explicit per-option guidance section.
- **Statistical noise.** Each `SelectedCohortV1` ships 14 numeric fields including `wilson95Low`,
  `wilson95High`, `adjustedQValue` and `differenceFromRestPercentagePoints`. Those exist so the
  *server* can decide a cohort is safe to narrate; the model is forbidden from citing them and
  cannot act on them. They are pure tokens.
- **Accidental coupling.** Adding a field to an internal selection record silently changes the
  prompt, invalidates the cache and can shift model behaviour with no code review signal.

**Change.** Add `agent/UnwrappedResearchPromptV1` (a wire shape: post question/summary/jurisdiction,
and per option — id, label, vote count, vote share, and per cohort — id, displayName, sampleSize,
optionVoteCount, propensityPercentage, overIndexPercentagePoints, relevanceReason). Map to it in the
generator. The prompt shape then changes only when someone edits the prompt DTO.

## 6. Deduplicate the rules across their five homes

The same rule is currently written in `UnwrappedSystemPrompt`, the user output contract, the
`@Description` schema annotations, `repairPrompt`, and `UnwrappedDraftValidator` — and they already
disagree in emphasis (headline "interesting insight" vs "catchy" vs "catchy"; paragraph rules
stated twice with different wording).

**Change.** One rule, one home:

- `UnwrappedDraftValidator` stays the machine truth (it is what actually rejects).
- `UnwrappedSystemPrompt.CONTRACT` is the single prose statement of those same rules; `repairPrompt`
  stops restating them and just names the failed code plus "re-read the contract".
- `@Description` annotations shrink to per-field *semantics* ("existing option id copied exactly
  from the input"), not global rules already in the contract.
- Delete lines the platform already enforces: "Return structured data only" (strict JSON schema is
  on) and "You must call web search before drafting" (`UnwrappedModelCustomizer` sets
  `ToolChoice.REQUIRED`).

---

## Tests

- Unit — new `UnwrappedPromptAssemblyTest`: user message contains no imperative contract text and
  parses as JSON; system message contains contract then guidance; a benchmark lane's guidance never
  removes contract clauses; repair note lands in the user turn.
- Unit — `UnwrappedResearchPromptV1` mapping pins the exact fields exposed to the model (this is the
  test that fails when someone adds a field to `SelectedCohortV1`).
- Existing — `UnwrappedDraftValidator` tests updated for the removed caveat field.
- Frontend — `UnwrappedBenchmarkPage.test.tsx` updated for guidance-only editors.
- Run `test-audit` afterwards.

## Sequencing

1 + 2 together (they are one refactor and 2 prevents 1 from breaking benchmarking), then 4, then 6
(cheap once the contract has one home), then 3, then 5. An ADR covers the message-role rule, the
fixed-contract/editable-guidance split, and the production repair-loop decision from 3.
