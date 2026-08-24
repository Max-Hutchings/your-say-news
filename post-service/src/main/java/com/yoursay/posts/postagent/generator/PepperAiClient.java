package com.yoursay.posts.postagent.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
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
        Result<AgentDraftDto> result = service.research(
                PepperSystemPrompt.DEFAULT,
                PepperSystemPrompt.OUTPUT_INSTRUCTIONS,
                request.trim());
        ChatResponse response = result.finalResponse();
        if (response == null) {
            throw invalid("LangChain4j returned no final provider response");
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
            throw invalid("LangChain4j returned no raw provider response for citation verification");
        }

        try {
            JsonNode root = objectMapper.readTree(metadata.rawHttpResponse().body());
            List<String> values = new ArrayList<>();
            addTextValues(root.path("citations"), values);
            addOpenAiAnnotationUrls(root, values);
            addOpenAiSearchActionUrls(root, values);
            return values.stream()
                    .filter(PepperAiClient::isHttpUrl)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new GenerationException(
                    "AGENT_PROVIDER_RESPONSE_INVALID",
                    "Could not read provider citations from the LangChain4j response",
                    false,
                    e
            );
        }
    }

    private static void addTextValues(JsonNode array, List<String> values) {
        if (!array.isArray()) return;
        array.forEach(item -> {
            if (item.isTextual()) values.add(item.asText());
        });
    }

    private static void addOpenAiAnnotationUrls(JsonNode root, List<String> values) {
        providerOutput(root).stream()
                .filter(item -> "message".equals(item.path("type").asText()))
                .flatMap(item -> streamArray(item.path("content")))
                .flatMap(content -> streamArray(content.path("annotations")))
                .forEach(annotation -> {
                    JsonNode url = annotation.path("url");
                    if ("url_citation".equals(annotation.path("type").asText())
                            && url.isTextual()) {
                        values.add(url.asText());
                    }
                });
    }

    private static void addOpenAiSearchActionUrls(JsonNode root, List<String> values) {
        providerOutput(root).stream()
                .filter(item -> "web_search_call".equals(item.path("type").asText()))
                .filter(item -> "completed".equals(item.path("status").asText()))
                .flatMap(item -> streamArray(item.path("action").path("sources")))
                .forEach(source -> {
                    JsonNode url = source.path("url");
                    if (url.isTextual()) values.add(url.asText());
                });
    }

    private static List<JsonNode> providerOutput(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) return List.of();
        List<JsonNode> values = new ArrayList<>();
        output.forEach(values::add);
        return values;
    }

    private static java.util.stream.Stream<JsonNode> streamArray(JsonNode node) {
        if (!node.isArray()) return java.util.stream.Stream.empty();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false);
    }

    private static boolean isHttpUrl(String value) {
        return value.regionMatches(true, 0, "https://", 0, 8)
                || value.regionMatches(true, 0, "http://", 0, 7);
    }

    private static GenerationException invalid(String message) {
        return new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID", message, false);
    }
}
