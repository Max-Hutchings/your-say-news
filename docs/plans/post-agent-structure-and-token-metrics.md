# Post-agent structure and token metrics

## Goal

Make Postagent's LangChain4j boundary match Unwrapped and expose reliable input/output token totals
for the whole system and each agent type.

## Structure

- Move provider-facing Postagent code from `generator` to the internal `agent` package.
- Add `PostAgentAiService`, returning `AgentDraftDto` directly rather than `Result<>`.
- Keep required-field validation in `postagent.validation`.
- Keep controllers and public service contracts at the `postagent` package root.

## Metrics

- Emit `yoursay.ai.tokens.total` once from each captured provider response.
- Use bounded `agent_type`, `token_type`, `model`, and `environment` labels.
- Track `input` and `output` tokens for `autopost`, `postagent`, and `unwrapped`.
- Add time-aware Grafana totals for the whole system and breakdowns per agent type.

## Verification

- Pin the direct AI-service return type and exact counter labels/values in unit tests.
- Run Postagent, Auto-post, Unwrapped, observability, dashboard JSON, and compile checks.
