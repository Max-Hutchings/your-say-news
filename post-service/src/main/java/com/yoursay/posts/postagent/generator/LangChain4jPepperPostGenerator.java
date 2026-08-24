package com.yoursay.posts.postagent.generator;

import com.yoursay.platform.ai.AiConfig;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LangChain4jPepperPostGenerator implements PepperPostGenerator {

    @Inject
    PepperAiClient client;

    @Inject
    AgentDraftValidator validator;

    @Inject
    AiConfig aiConfig;

    @Override
    public GenerationResult generate(String request) {
        if (!aiConfig.pepper().configured()) {
            throw new GenerationException("AGENT_PROVIDER_NOT_CONFIGURED",
                    "The selected AI provider API key is not configured", false);
        }

        try {
            PepperAiResponse response = client.research(request);
            validator.validate(response.draft(), response.citations());
            return new GenerationResult(response.draft(),
                    valueOr(response.model(), aiConfig.pepper().model()),
                    response.providerResponseId());
        } catch (GenerationException e) {
            throw e;
        } catch (RetriableException e) {
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "AI provider request failed and may be retried", true, e);
        } catch (NonRetriableException e) {
            throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                    "AI provider rejected the request or returned invalid output", false, e);
        } catch (RuntimeException e) {
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "AI provider request failed", true, e);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
