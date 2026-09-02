# Switchable agent AI providers

## Goal

Keep Pepper, AutoPost and Unwrapped on their existing `@RegisterAiService` interfaces while one
application property selects OpenAI or Grok for every service.

## Decisions

- `agent.provider=openai|grok` selects the shared Responses API endpoint, credential and default
  model. OpenAI is the default.
- Deployment writes only the selected credential to the service environment under
  `AGENT_API_KEY`; the unselected provider secret is not exposed to the container.
- Each agent retains a provider-neutral named model (`pepper`, `autopost`, `unwrapped`) and may
  override its key, URL or model without changing Java code.
- All three models require server-side `web_search`. OpenAI includes search action sources; Grok
  disables inline citations so they cannot corrupt structured JSON.
- Citation verification accepts only provider evidence from the documented Responses API output
  locations.

## Verification

- Unit tests resolve both providers for every registered model and pin Responses mode.
- Model tests require web search, provider-specific metadata and exact named-model qualifiers.
- Citation tests cover xAI top-level citations, OpenAI annotations/action sources and decoy data.
- Existing domain metrics and LangChain4j GenAI telemetry continue to measure the same external
  operation boundaries, outcomes, latency, provider and model. No dashboard query changes are
  required.
