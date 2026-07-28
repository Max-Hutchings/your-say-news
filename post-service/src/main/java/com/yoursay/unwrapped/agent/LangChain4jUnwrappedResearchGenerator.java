package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.Result;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/** LangChain4j implementation kept behind the provider-neutral Unwrapped domain interface. */
@ApplicationScoped
public class LangChain4jUnwrappedResearchGenerator implements UnwrappedResearchGenerator {
    private static final String NOT_CONFIGURED = "__not_configured__";

    @Inject
    UnwrappedResearchAiService aiService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    UnwrappedDraftValidator validator;

    @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = "__not_configured__")
    String apiKey;

    @ConfigProperty(name = "unwrapped.agent.model", defaultValue = "configured-model")
    String configuredModel;

    @Override
    public UnwrappedResearchResult generate(UnwrappedResearchRequest request) {
        if (apiKey == null || apiKey.isBlank() || NOT_CONFIGURED.equals(apiKey)) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_NOT_CONFIGURED");
        }
        try {
            Result<UnwrappedResearchDraftV1> result =
                    aiService.research(objectMapper.writeValueAsString(request));
            ChatResponse response = result.finalResponse();
            if (response == null || result.content() == null) {
                throw new IllegalStateException("UNWRAPPED_PROVIDER_RESPONSE_MISSING");
            }
            List<String> citations = citations(response);
            validator.validate(request, result.content(), citations);
            return new UnwrappedResearchResult(result.content(), citations,
                    valueOr(response.modelName(), configuredModel), response.id());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_FAILURE", e);
        }
    }

    private List<String> citations(ChatResponse response) {
        if (!(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)
                || metadata.rawHttpResponse() == null || metadata.rawHttpResponse().body() == null) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_MISSING");
        }
        try {
            JsonNode array = objectMapper.readTree(metadata.rawHttpResponse().body()).path("citations");
            List<String> values = new ArrayList<>();
            if (array.isArray()) {
                array.forEach(item -> {
                    if (item.isTextual()) values.add(item.asText());
                });
            }
            return List.copyOf(values);
        } catch (Exception e) {
            throw new IllegalStateException("UNWRAPPED_PROVIDER_CITATIONS_INVALID", e);
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
