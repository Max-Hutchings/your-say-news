# ADR-051 - LangChain4j agent service contract

Date: 2026-08-24

## Situation

Pepper, AutoPost discovery and Post Unwrapped now use the same successful LangChain4j structure:
a small declarative AI service interface returns typed structured output, while a separate adapter
handles provider metadata, validation and domain mapping.

Earlier versions mixed long prompts into Java annotations, returned loosely handled provider
responses, or repeated the same rules in prompts, schema descriptions and validators. This made
prompts hard to review, allowed instructions to drift, and blurred the boundary between model
output and trusted application data.

Future agents need one standard structure that preserves the lessons from these implementations.

## Options considered

1. Let each agent choose its own LangChain4j interface, prompt and response conventions.
2. Call provider APIs directly and parse free-form JSON in each agent.
3. Standardise a declarative LangChain4j service, annotated response records and a validating
   adapter around them.

## Decision

Choose option 3.

### AI service interface

Every agent must have one role-specific, provider-neutral `@RegisterAiService` interface in the
domain that owns the behaviour. It must:

- select a unique configured model with `modelName`;
- disable chat memory with `NoChatMemoryProviderSupplier` for independent one-shot operations;
- contain only the LangChain4j method contract, with no orchestration, validation or domain logic;
- use `@SystemMessage` template variables for the stable editorial prompt and output instructions;
- use `@UserMessage` only for facts and instructions that vary for the current request; and
- return a typed structured-output record, not free-form JSON or prose.

The standard shape is:

```java
@RegisterAiService(
        modelName = "agent-name",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
interface ExampleAiService {
    @SystemMessage("""
            {{systemPrompt}}

            {{outputInstructions}}
            """)
    ExampleDraft generate(
            @V("systemPrompt") String systemPrompt,
            @V("outputInstructions") String outputInstructions,
            @UserMessage String request);
}
```

Return the structured-output record directly. Provider metadata, including token usage, must be
captured through a request-scoped `ChatModelListener`; it is not a reason to expose LangChain4j's
`Result` wrapper. LangChain4j types must remain inside the technical adapter and must not become
domain or REST contracts.

Agent implementations live in an internal `agent` technical package under the owning domain. This
keeps Postagent aligned with Unwrapped: the generated AI service, response capture, provider
configuration and adapter are together, while validation has its own `validation` package.

### Prompts and message roles

Long prompts must live in version-controlled Markdown resources under
`post-service/src/main/resources/prompts/<agent>/`. A small prompt loader exposes the editorial
prompt and output instructions to the service call.

Stable identity, editorial behaviour and output rules belong in the system message. Per-call input,
such as a topic, time window or aggregate result, belongs in the user message. Splitting editorial
guidance from output instructions keeps the schema contract visible and allows guidance to be
benchmarked without silently removing output requirements.

Prompt input should use an explicit model-facing request type when an internal request contains
fields the model does not need. This prevents unrelated domain changes from silently changing the
prompt or wasting tokens.

### Writing output instructions

`output-instructions.md` is the complete model-facing output contract. Do not rely on the generated
JSON schema or Java `@Description` annotations to communicate cardinality, length or cross-field
rules. A response can match the schema while still being unusable, as happened when Pepper returned
the right fields but violated rules that were not stated precisely in its output instructions.

Write the contract as short, imperative and independently testable requirements. Follow the shape
of the response record and name fields exactly, using backticks. State every bounded rule explicitly:

- exact list counts or allowed ranges, such as exactly three `summaryClaims`;
- required ordering, uniqueness and whether entries may be omitted;
- word, character, sentence and paragraph limits;
- exact enum-dependent behaviour, including required labels and option counts;
- cross-field integrity, such as every referenced source appearing exactly once and no source being
  returned unused;
- required tool use and evidence provenance, such as using only URLs returned by web search in the
  same call; and
- fixed safety, privacy or caveat text where variation is not allowed.

Avoid requirements such as "be concise", "include enough sources" or "use suitable options" when
the application needs a measurable result. Replace them with limits and acceptance conditions. Put
format examples after the rules when exact Markdown or text structure matters, as the Unwrapped
agent does for selected-group paragraphs.

The three contract layers have different purposes and must remain aligned:

1. `output-instructions.md` tells the model the full field and cross-field contract.
2. `@Description` tells the generated schema the local meaning and bounds of each field. Keep these
   descriptions specific, but do not copy the entire output contract into every annotation.
3. Deterministic Java validation rejects only missing, empty or blank required fields. It must not
   repeat model-facing counts, writing limits, ordering or editorial rules as application rejection
   rules.

Focused tests must pin important wording in the Markdown contract and field descriptions. Validator
tests must prove populated model output is accepted and missing or blank fields are rejected with a
stable required-field error. Model text verbosity should be set to `low` for bounded agent output,
but it is only a style control. It does not replace explicit field limits in the model instructions.
Likewise, the provider output-token limit is a final safety boundary, not the way to make individual
fields concise.

### Structured response records

Model output must be represented by Java records with concrete field types, nested records, enums
and lists. Use a model-facing `Draft` name, with a version suffix when the shape is persisted or
expected to evolve. Do not make an untrusted model record a domain entity.

Add LangChain4j `@Description` annotations to the root record and to every component whose meaning
is not fully clear from its Java name and type. Descriptions define the generated schema in short,
specific language. They should state local semantics such as:

- what the value represents;
- the required format or unit;
- the allowed value set where the Java type does not already express it; and
- whether a list is ordered or what its entries represent.

Descriptions must not become a second copy of the full prompt. Cross-field rules, editorial
behaviour, cardinality and writing limits belong in output instructions. Java validation checks
only that the required model fields are populated.

Example:

```java
@Description("A ranked current news story returned by the research provider")
record ExampleStoryDraft(
        @Description("Unique rank from 1, most important, through 10") int rank,
        @Description("Neutral concise headline") String headline,
        @Description("ISO-8601 UTC timestamp, for example 2026-08-23T12:34:56Z")
        String publishedAt
) {
}
```

### Adapter and trust boundary

The generated AI service must sit behind a provider-neutral domain interface. Its adapter is
responsible for:

- assembling the prompt and invoking the AI service;
- rejecting null, unparsable or blank required output fields;
- mapping model-facing drafts into domain types;
- translating provider and parsing failures into stable domain faults; and
- collecting model name, response ID, token usage or citations when the operation requires them.

Structured output proves only that a response matches a schema. It does not prove that claims,
URLs or relationships are true. Evidence verification is a separate product or safety decision,
not part of the default output validator. Read raw provider metadata from the `ChatModelListener`
response capture because Quarkiverse can omit it from the ordinary AI service result.

Every successful provider response records its reported input and output tokens in the shared
`yoursay.ai.tokens.total` counter. Its bounded labels are `agent_type`, `token_type`, `model` and
`environment`. `agent_type` is one of `autopost`, `postagent` or `unwrapped`; `token_type` is either
`input` or `output`. This supports system-wide totals and per-agent totals without prompt text,
user identifiers or other high-cardinality data in metrics.

Provider configuration, response parsing and LangChain4j exceptions must not leak through the
domain's public interface. Observability belongs around the adapter operation and must use bounded,
PII-safe fields as defined by the repository's observability standard.

Every agent adapter passes its captured provider response to the shared failure-response logger
when response parsing, evidence inspection, mapping, or required-field validation fails. Local
development enables an `ai_failure_response` event containing the complete raw response, stable
fault code, and trace ID so malformed output can be diagnosed. The event is disabled by default,
including production and tests, because model output is unbounded and may contain sensitive data.
Response content must never be used in metric attributes or persisted domain errors.

## Reason

A declarative typed interface lets LangChain4j generate and parse the structured-output schema
without hand-written provider JSON code. Annotated records make the model contract readable next
to the Java types, while Markdown prompts remain easy to review and benchmark.

Keeping validation and provider evidence outside the response record preserves the essential trust
boundary: model output is a draft, not application truth. The provider-neutral adapter also lets
the configured AI provider change without altering domain contracts.

## Consequences and follow-up work

- New agent reviews must check the service interface, prompt resources, annotated response records,
  required-field validator and adapter as one contract.
- A response-schema change requires focused tests that pin the generated shape and validation
  behaviour. Persisted or externally consumed shapes require an explicit version change.
- Prompt tests should prove that stable instructions are in the system message and per-call data is
  in the user message, and that the output Markdown states every important measurable constraint.
- Field-description tests must pin model-facing bounds. Validator tests must cover only missing,
  empty and blank required fields.
- Provider contract tests should cover invalid structured output, missing required metadata and
  missing or invented citations where research evidence is required.
- Conversational agents that genuinely need memory require a separate decision defining memory
  ownership, retention, privacy and expiry before chat memory is enabled.
