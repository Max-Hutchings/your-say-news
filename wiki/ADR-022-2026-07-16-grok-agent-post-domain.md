# ADR-022 — Grok agent in post-service

## Situation

MVP1 Stage 7 creates sourced, balanced post drafts through live web research. The roadmap originally
placed a Claude-based agent in a separate service. The expected work is mostly asynchronous and
closely coupled to post drafting, media review and final post publication. A separate deployment
would add operational and cross-service consistency cost before there is evidence that the workload
needs independent scaling.

xAI's Grok models provide live web-search tools, citations and structured outputs at competitive
token prices. Grok can also return image-search results, but a relevant image URL does not establish
that Your Say News may lawfully republish the image.

## Options considered

1. Keep a separately deployed Claude agent-service.
2. Put a Grok-specific implementation directly into the existing `posts` domain.
3. Add a single `agent` DDD domain inside `post-service`, with Grok behind a provider-neutral
   interface.
4. Add a top-level `agents` package with separate `postagent` and `unwrappedagent` subdomains.

For images:

1. automatically publish an image returned by Grok web/image search;
2. generate every image with an image model; or
3. keep human media selection for MVP1 and let the agent provide only an image brief/search query.

## Decision

Create a top-level `agents` package inside `post-service`, with role-specific subdomains beneath it.
`postagent` owns durable asynchronous post-generation jobs, provider orchestration, sourced draft
validation and the review/publish workflow. `unwrappedagent` separately owns the future cached
Post Unwrapped analysis produced after voting. The two subdomains must not share domain contracts
or implementations merely because they may use the same model provider. `postagent` crosses into
post creation only through the public `PostService` contract.

Use Quarkus LangChain4j's declarative AI service and OpenAI-compatible model integration to call the
xAI Responses API with live web search and strict structured output. Default to configurable
`grok-4.3` with low reasoning effort as the value option; allow configuration to select Grok 4.5 or
a later evaluated model. Persist the model used for every job. Do not duplicate provider transport,
structured-output schema generation or response conversion in a hand-written REST client.

Require every generated claim to list supporting URLs and verify those URLs were returned in the
provider's collected search citations. Provider output fails closed when source mapping is absent or
invalid.

For MVP1, the human chooses and uploads post media through the existing pipeline. The agent may
produce an image brief and search query, but it must not import an arbitrary image-search result.
AI-generated illustrations are a possible later opt-in only when clearly labelled as illustrations,
not documentary photographs.

## Reason

Role-specific subdomain boundaries preserve extraction options without paying for another
deployment now. They also prevent post drafting and post-vote analysis from becoming one agent with
two unrelated responsibilities. Durable jobs handle latency and retries inside the service that
already owns the resulting post, making approval and publication easier to keep idempotent.

Grok's web search and structured output fit the research workflow, while configuration avoids
binding the domain contract to one model version. Validating claim URLs against provider citations
does not prove a source is true, but it prevents the model from fabricating source links and gives
the reviewer traceable evidence.

Human media selection is the safest MVP boundary. General web image search is not licence
verification, and generated news-like images can mislead readers about whether a depicted event
actually occurred.

## Consequences and follow-up work

- The MVP1 service map remains two deployables: `user-service` and `post-service`.
- `post-service` gains a top-level `agents` package with `postagent` and `unwrappedagent`
  subdomains. The existing durable generation job belongs only to `postagent`.
- `unwrappedagent` receives its own public contract, persistence model and orchestration when Post
  Unwrapped analysis is implemented; it does not reuse post-generation jobs or DTOs.
- xAI credentials stay server-side and are never exposed to the mobile client or persisted.
- Generation cost, latency, retries, source count and failure reason need aggregate telemetry.
- Model/prompt versions must be recorded so quality regressions can be evaluated.
- The publish UI must require human review and must not describe machine output as guaranteed
  unbiased.
- A future image assistant requires a licence-aware API, stored attribution/provenance and another
  review of commercial-use and personality/privacy rights.
