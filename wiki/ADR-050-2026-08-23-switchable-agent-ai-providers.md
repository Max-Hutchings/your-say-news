# ADR-050 - Switchable agent AI providers

## Situation

Pepper, AutoPost discovery and Unwrapped research were tied to xAI configuration even though they
already used Quarkus LangChain4j's OpenAI-compatible Responses client. Provider names also leaked
into implementation class names and error text. Changing provider required several coordinated
code and deployment edits.

Every research agent requires current web evidence and must preserve its structured output and
source-verification boundary.

## Options considered

1. Duplicate every `@RegisterAiService` interface and select a CDI implementation.
2. Keep one registered interface per agent and select the compatible Responses endpoint through
   configuration.
3. Remove LangChain4j and build separate provider SDK adapters.

## Decision

Keep one provider-neutral `@RegisterAiService` interface per agent. The application property
`agent.provider` selects `openai` or `grok`; OpenAI is the default. Provider-specific endpoint,
credential, model and Responses metadata settings sit behind that selection. Each agent keeps its
own named model, token budget and prompt cache.

Application-level AI settings are injected once by `com.yoursay.platform.ai.AiConfig`. Pepper,
AutoPost, Unwrapped and the shared web-search customizer consume its typed groups instead of
scattering `@ConfigProperty` fields across domain implementations.

All agent models require the server-side `web_search` tool. OpenAI source evidence is read only
from web-search actions and message URL annotations. Grok's documented top-level citations remain
supported. Provider response evidence is never trusted from unrelated output nodes.

## Reason

The providers expose the same Responses contract through the existing LangChain4j integration, so
duplicating prompt interfaces would add drift without adding isolation. One selection property
makes failover and trust decisions operational while preserving domain-facing interfaces.

OpenAI as the default removes the existing operational dependency on Grok. Keeping Grok available
provides a reversible fallback.

## Consequences and follow-up

- Deployments must supply the key for the selected provider. OpenAI uses `OPENAI_API_KEY`; Grok uses
  `YOUR_SAY_NEWS_GROK_API_KEY` locally or `XAI_API_KEY` in deployment.
- Deployment renders that selected secret as `AGENT_API_KEY` and does not place the unselected
  provider credential in the service environment.
- `PEPPER_*`, `AUTOPOST_*` and `UNWRAPPED_*` properties can override the selected defaults for one
  agent.
- New application-level AI settings belong in `platform.ai.AiConfig`; domain implementations must
  consume that central configuration rather than inject the property directly.
- Existing operation metrics, fault classification, structured logs and LangChain4j GenAI traces
  continue unchanged. Provider and model remain visible through the existing GenAI telemetry.
- A live provider contract test remains opt-in because it consumes paid external APIs.
