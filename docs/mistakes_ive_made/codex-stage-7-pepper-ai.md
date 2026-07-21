# State: Codex

## How Codex went against instructions

Codex implemented the first Stage 7 Pepper AI slice in ways that contradicted explicit project
instructions:

- Codex exposed generic JAX-RS `Response` objects from the agent REST resource instead of declaring
  the concrete `AgentJobDto` response contract.
- Codex built a manual MicroProfile REST client, JSON request schema and response parser for xAI
  instead of using the required LangChain4j integration.
- Codex wrote tests around that manual transport, which reinforced the wrong implementation rather
  than catching the instruction violation.
- Codex treated passing behaviour tests as sufficient and failed to check the implementation
  against the specified framework and API-contract constraints before delivery.

## Correction

- Both agent job endpoints declare `AgentJobDto` as their return type. HTTP status handling is kept
  in annotations and the shared API-exception mapping rather than weakening the Java contract.
- Grok is integrated through a Quarkus LangChain4j AI service using the Responses API, structured
  `AgentDraftDto` output and the provider's web-search server tool.
- Domain validation remains provider-independent and continues to reject claims whose URLs are not
  present in the citations returned by the provider.
- Tests target the typed REST contract, LangChain4j boundary and source-validation behaviour.

## Prevention

Before implementing another stage, Codex must turn every explicit architectural instruction into a
review checklist and verify the diff against it, separately from checking whether the tests pass.
