package com.yoursay.autopost.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.StreamSupport;

/** Verifies that the selected provider completed the required server-side web-search operation. */
@ApplicationScoped
class AutoPostProviderResponseInspector {

    @Inject
    ObjectMapper objectMapper;

    void requireCompletedWebSearch(ChatResponse response) {
        ProviderResponse providerResponse = readProviderResponse(response);
        JsonNode output = providerResponse.output();
        long webSearchCalls = StreamSupport.stream(output.spliterator(), false)
                .filter(AutoPostProviderResponseInspector::isWebSearchCall)
                .count();
        if (webSearchCalls == 0) {
            throw fault(
                    "AUTO_POST_WEB_SEARCH_MISSING",
                    "provider_contract",
                    "The provider returned no evidence of the required live web search");
        }

        boolean completed = StreamSupport.stream(output.spliterator(), false)
                .filter(AutoPostProviderResponseInspector::isWebSearchCall)
                .anyMatch(item -> "completed".equals(item.path("status").asText()));
        if (!completed) {
            throw fault(
                    "AUTO_POST_WEB_SEARCH_FAILED",
                    "dependency",
                    "The provider did not complete the required live web search");
        }
    }

    private ProviderResponse readProviderResponse(ChatResponse response) {
        SuccessfulHttpResponse rawResponse = rawResponse(response);
        if (rawResponse == null || rawResponse.body() == null || rawResponse.body().isBlank()) {
            throw fault(
                    "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                    "provider_contract",
                    "The provider response contained no inspectable research evidence");
        }
        try {
            JsonNode output = objectMapper.readTree(rawResponse.body()).path("output");
            if (!output.isArray()) {
                throw fault(
                        "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                        "provider_contract",
                        "The provider response contained no inspectable research output");
            }
            return new ProviderResponse(output);
        } catch (JsonProcessingException error) {
            throw new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                    "provider_contract",
                    "provider_evidence",
                    "The provider research evidence could not be parsed",
                    false,
                    error);
        }
    }

    private static SuccessfulHttpResponse rawResponse(ChatResponse response) {
        if (response == null
                || !(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)) {
            return null;
        }
        return metadata.rawHttpResponse();
    }

    private static boolean isWebSearchCall(JsonNode item) {
        return "web_search_call".equals(item.path("type").asText());
    }

    private record ProviderResponse(JsonNode output) {
    }

    private static AutoPostDiscoveryException fault(
            String code,
            String faultType,
            String message
    ) {
        return new AutoPostDiscoveryException(
                code,
                faultType,
                "web_search",
                message,
                false,
                null);
    }
}
