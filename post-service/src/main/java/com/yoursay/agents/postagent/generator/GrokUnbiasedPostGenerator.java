package com.yoursay.agents.postagent.generator;

import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class GrokUnbiasedPostGenerator implements UnbiasedPostGenerator {

    private static final String API_KEY_NOT_CONFIGURED = "__not_configured__";

    @Inject
    PepperAiClient client;

    @Inject
    AgentDraftValidator validator;

    @ConfigProperty(name = "agent.grok.api-key")
    String apiKey;

    @ConfigProperty(name = "agent.grok.model", defaultValue = "grok-4.3")
    String model;

    @Override
    public GenerationResult generate(String request) {
        if (apiKey == null || apiKey.isBlank() || API_KEY_NOT_CONFIGURED.equals(apiKey)) {
            throw new GenerationException("AGENT_PROVIDER_NOT_CONFIGURED",
                    "XAI_API_KEY is not configured", false);
        }

        try {
            PepperAiResponse response = client.research(request);
            validator.validate(response.draft(), response.citations());
            return new GenerationResult(response.draft(), valueOr(response.model(), model),
                    response.providerResponseId());
        } catch (GenerationException e) {
            throw e;
        } catch (RetriableException e) {
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "Grok request failed and may be retried", true, e);
        } catch (NonRetriableException e) {
            throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                    "Grok rejected the request or returned invalid output", false, e);
        } catch (RuntimeException e) {
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "Grok request failed", true, e);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
