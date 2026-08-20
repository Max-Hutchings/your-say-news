package com.yoursay.autopost.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.Result;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
class AutoPostAiClient {

    @Inject
    AutoPostResearchAiService service;

    @Inject
    ObjectMapper objectMapper;

    StoryDiscoveryResult discover(Instant windowStart, Instant windowEnd) {
        String instruction = "Window start inclusive: %s%nWindow end inclusive: %s"
                .formatted(windowStart, windowEnd);
        Result<StoryDiscoveryDraft> result = service.discover(instruction);
        ChatResponse response = result.finalResponse();
        if (response == null || result.content() == null) {
            throw invalid("The model returned no discovery response");
        }
        return new StoryDiscoveryResult(result.content().stories(), response.modelName(), response.id(),
                citations(response));
    }

    private List<String> citations(ChatResponse response) {
        if (!(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)
                || metadata.rawHttpResponse() == null
                || metadata.rawHttpResponse().body() == null) {
            throw invalid("The model returned no raw response for citation verification");
        }
        try {
            JsonNode values = objectMapper.readTree(metadata.rawHttpResponse().body()).path("citations");
            List<String> citations = new ArrayList<>();
            if (values.isArray()) {
                values.forEach(value -> {
                    if (value.isTextual()) {
                        citations.add(value.asText());
                    }
                });
            }
            return List.copyOf(citations);
        } catch (Exception error) {
            throw new AutoPostDiscoveryException("AUTO_POST_PROVIDER_RESPONSE_INVALID",
                    "Could not read discovery citations", false, error);
        }
    }

    private static AutoPostDiscoveryException invalid(String message) {
        return new AutoPostDiscoveryException("AUTO_POST_PROVIDER_RESPONSE_INVALID", message, false);
    }
}
