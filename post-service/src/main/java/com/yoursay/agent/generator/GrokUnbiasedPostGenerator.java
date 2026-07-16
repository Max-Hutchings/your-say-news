package com.yoursay.agent.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agent.AgentDraftDto;
import com.yoursay.agent.client.XaiResponsesClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class GrokUnbiasedPostGenerator implements UnbiasedPostGenerator {

    private static final String API_KEY_NOT_CONFIGURED = "__not_configured__";

    @RestClient
    XaiResponsesClient client;

    @Inject
    GrokPromptFactory promptFactory;

    @Inject
    AgentDraftValidator validator;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "agent.grok.api-key")
    String apiKey;

    @ConfigProperty(name = "agent.grok.model", defaultValue = "grok-4.3")
    String model;

    @ConfigProperty(name = "agent.grok.reasoning-effort", defaultValue = "low")
    String reasoningEffort;

    @ConfigProperty(name = "agent.grok.max-output-tokens", defaultValue = "4000")
    int maxOutputTokens;

    @Override
    public GenerationResult generate(String request) {
        if (apiKey == null || apiKey.isBlank() || API_KEY_NOT_CONFIGURED.equals(apiKey)) {
            throw new GenerationException("AGENT_PROVIDER_NOT_CONFIGURED",
                    "XAI_API_KEY is not configured", false);
        }

        JsonNode payload = promptFactory.create(request, model, reasoningEffort, maxOutputTokens);
        try (Response response = client.createResponse("Bearer " + apiKey, payload)) {
            int status = response.getStatus();
            JsonNode body = response.hasEntity() ? response.readEntity(JsonNode.class) : null;
            if (status >= 400) {
                boolean retryable = status == 429 || status >= 500;
                throw new GenerationException("AGENT_PROVIDER_HTTP_" + status,
                        "xAI response failed with status " + status, retryable);
            }
            return parse(body);
        } catch (GenerationException e) {
            throw e;
        } catch (ProcessingException e) {
            throw new GenerationException("AGENT_PROVIDER_UNAVAILABLE",
                    "xAI request failed", true, e);
        } catch (Exception e) {
            throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                    "Could not parse xAI response", false, e);
        }
    }

    private GenerationResult parse(JsonNode response) throws Exception {
        if (response == null) {
            throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                    "xAI returned an empty response", false);
        }
        String outputText = findOutputText(response);
        AgentDraftDto draft = objectMapper.readValue(outputText, AgentDraftDto.class);
        List<String> citations = strings(response.path("citations"));
        validator.validate(draft, citations);

        String responseModel = textOr(response, "model", model);
        String responseId = textOr(response, "id", null);
        return new GenerationResult(draft, responseModel, responseId);
    }

    private static String findOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            if (!"message".equals(output.path("type").asText())) {
                continue;
            }
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())
                        && !content.path("text").asText().isBlank()) {
                    return content.path("text").asText();
                }
            }
        }
        throw new GenerationException("AGENT_PROVIDER_RESPONSE_INVALID",
                "xAI response contained no output_text", false);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText();
        return value.isBlank() ? fallback : value;
    }
}
