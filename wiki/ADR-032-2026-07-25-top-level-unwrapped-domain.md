# ADR-032 — Top-level Post Unwrapped domain

Date: 2026-07-25

## Situation

The initial backend structure reserved `com.yoursay.agents.unwrappedagent` for Post Unwrapped
because story generation uses a language model. The product design has since expanded beyond agent
generation. Post Unwrapped owns:

- deterministic aggregate evidence selection;
- prediction and observed story versions;
- durable milestone jobs;
- source validation and administrator review;
- story delivery;
- the reconsideration response; and
- an internal LangChain4j research/writing adapter.

Treating this whole capability as an agent subdomain makes the implementation mechanism appear to be
the domain. It also places public story, review and follow-up contracts under an `agents` namespace
even though most of their behaviour is not model-specific.

## Options considered

### 1. Keep `com.yoursay.agents.unwrappedagent`

This preserves the existing empty marker and the package choice in ADR-022.

It makes the language-model adapter the apparent aggregate root and weakens the boundary between
model integration and the larger user-facing product capability.

### 2. Create `com.yoursay.unwrapped` and keep the agent elsewhere

The top-level domain would own stories and follow-up responses, while
`com.yoursay.agents.unwrappedagent` would generate drafts through a public contract.

This gives Post Unwrapped a proper boundary, but creates two domains for one cohesive workflow and
requires a cross-domain contract solely because one implementation calls a model.

### 3. Create `com.yoursay.unwrapped` with an internal `agent` package

The top-level domain owns the complete workflow. Its model-agnostic generator interface sits at the
domain's public/application boundary as appropriate, and LangChain4j implementation details live
under `com.yoursay.unwrapped.agent`.

## Decision

Choose option 3.

`com.yoursay.unwrapped` is a top-level DDD domain inside `post-service`, beside `posts`, `votes`,
`feed` and `topics`.

Its public face follows the repository's standard domain rules:

```text
com.yoursay.unwrapped/
  UnwrappedController.java
  UnwrappedService.java
  <public DTOs>
  agent/          <- internal LangChain4j/model-provider adapter
  model/          <- internal entities/repositories
  service/        <- internal orchestration and domain-service implementations
  selection/      <- internal deterministic cohort/insight selection
  validation/     <- internal story and source validation
```

The `agent` package is not a nested domain. It is a technical implementation concern owned by
Unwrapped. The domain-level generation contract is model-agnostic; model names, provider response
types and provider-specific citation extraction remain inside `agent`.

`com.yoursay.agents` continues to contain role-specific official publishing agents such as
`postagent` and `ysnagent`. Those agents orchestrate creation/publication as their primary domain
responsibility. Post Unwrapped does not belong there merely because one internal step uses
LangChain4j.

This decision supersedes only the placement of `unwrappedagent` in ADR-022 and the corresponding
namespace lists in ADR-027. It does not change the separate `postagent` or `ysnagent` boundaries.

## Reason

DDD packages should describe business capabilities, not libraries or implementation mechanisms.
Users experience Post Unwrapped as one product journey, and its correctness depends at least as much
on aggregation, versioning, review and follow-up isolation as it does on generated prose.

Keeping the model adapter internal makes provider replacement mechanical and prevents the public
story contract from inheriting LangChain4j or provider types. The top-level package also makes later
service extraction possible without splitting one cohesive workflow first.

## Consequences and follow-up work

- Remove the empty `com.yoursay.agents.unwrappedagent` marker.
- Add `com.yoursay.unwrapped` and `com.yoursay.unwrapped.agent` package markers.
- Place future public controllers, interfaces and DTOs directly under `com.yoursay.unwrapped`.
- Keep entities, repositories, selection, orchestration, validation and LangChain4j implementation
  in internal technical subpackages.
- Update `CLAUDE.md`, the active roadmap, remaining-work checklist and architecture plan.
- ADR-028 continues to define aggregate evidence selection.
- ADR-029 continues to define the versioned story lifecycle.
- No database or API migration is required because `unwrappedagent` had no implementation or
  public contract.
