package com.yoursay.posts.postagent.agent;

import com.yoursay.platform.ai.AiConfig;
import com.yoursay.platform.ai.AiFailureResponseLog;
import com.yoursay.platform.ai.AiProviderFailureLog;
import com.yoursay.posts.postagent.validation.AgentDraftValidator;
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

    @Inject
    AiProviderFailureLog providerFailureLog;

    @Inject
    AiFailureResponseLog failureResponseLog;

    @Override
    public GenerationResult generate(String request) {
        if (!aiConfig.pepper().configured()) {
            throw new GenerationException("AGENT_PROVIDER_NOT_CONFIGURED",
                    "The selected AI provider API key is not configured", false);
        }

        PepperAiResponse response = null;
        try {
            response = client.research(request);
            validator.validate(response.draft());
            return new GenerationResult(response.draft(),
                    valueOr(response.model(), aiConfig.pepper().model()),
                    response.providerResponseId());
        } catch (GenerationException e) {
            logFailureResponse(e.code(), response);
            logProviderHttpFailure(e.code(), e);
            throw e;
        } catch (RetriableException e) {
            logProviderHttpFailure("AGENT_PROVIDER_UNAVAILABLE", e);
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "AI provider request failed and may be retried", true, e);
        } catch (NonRetriableException e) {
            logProviderHttpFailure("AGENT_PROVIDER_RESPONSE_INVALID", e);
            throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                    "AI provider rejected the request or returned invalid output", false, e);
        } catch (RuntimeException e) {
            logProviderHttpFailure("AGENT_PROVIDER_UNAVAILABLE", e);
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "AI provider request failed", true, e);
        }
    }

    private void logFailureResponse(String faultCode, PepperAiResponse response) {
        if (response != null) {
            failureResponseLog.log(
                    "postagent", "generation", faultCode, response.rawResponse());
        }
    }

    private void logProviderHttpFailure(String faultCode, RuntimeException failure) {
        providerFailureLog.logNonSuccessResponse(aiConfig.provider(), "postagent",
                "generation", faultCode, failure);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
