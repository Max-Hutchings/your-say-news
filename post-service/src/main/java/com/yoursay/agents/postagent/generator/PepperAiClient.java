package com.yoursay.agents.postagent.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agents.postagent.AgentDraftDto;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.Result;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
class PepperAiClient {

    @Inject
    PepperAiService service;

    @Inject
    ObjectMapper objectMapper;

    PepperAiResponse research(String request) {
        Result<AgentDraftDto> result = service.research(request.trim());
        ChatResponse response = result.finalResponse();
        if (response == null) {
            throw invalid("LangChain4j returned no final Grok response");
        }

        return new PepperAiResponse(
                result.content(),
                citations(response),
                response.modelName(),
                response.id()
        );
    }

    private List<String> citations(ChatResponse response) {
        if (!(response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata)
                || metadata.rawHttpResponse() == null
                || metadata.rawHttpResponse().body() == null) {
            throw invalid("LangChain4j returned no raw Grok response for citation verification");
        }

        try {
            JsonNode array = objectMapper.readTree(metadata.rawHttpResponse().body()).path("citations");
            List<String> values = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    if (item.isTextual()) {
                        values.add(item.asText());
                    }
                }
            }
            return List.copyOf(values);
        } catch (Exception e) {
            throw new GenerationException(
                    "AGENT_PROVIDER_RESPONSE_INVALID",
                    "Could not read Grok citations from the LangChain4j response",
                    false,
                    e
            );
        }
    }

    private static GenerationException invalid(String message) {
        return new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID", message, false);
    }
}
