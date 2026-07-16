package com.yoursay.agent.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class GrokPromptFactory {

    static final String PROMPT_VERSION = "unbiased-post-v1";

    private static final String SYSTEM_PROMPT = """
            You are the research and balanced-drafting engine for Your Say News.
            Research the user's requested current-affairs subject using live web search.

            Produce a neutral factual overview, the strongest material case for the motion, the
            strongest material case against it, and one concise support question. Do not manufacture
            symmetry: if evidence is lopsided, say so while still representing the strongest genuine
            objection. Clearly distinguish verified facts, forecasts, allegations and opinion.
            Prefer primary sources, official data and strong independent reporting. Use multiple
            publishers where possible. Avoid loaded language, advocacy, rhetorical questions and
            unsupported claims.

            Every claim must list one or more exact source URLs. Only cite URLs you actually found
            through search. The sources array must describe every URL used by a claim. Also provide
            a factual image brief and a search query for a human editor; do not assert that any found
            image is licensed for reuse.
            """;

    private final ObjectMapper objectMapper;

    public GrokPromptFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode create(String request, String model, String reasoningEffort, int maxOutputTokens) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("prompt_cache_key", PROMPT_VERSION);
        root.put("max_output_tokens", maxOutputTokens);
        root.set("input", input(request));
        root.set("tools", tools());
        root.set("reasoning", objectMapper.createObjectNode().put("effort", reasoningEffort));
        root.set("include", objectMapper.createArrayNode().add("no_inline_citations"));

        ObjectNode format = objectMapper.createObjectNode()
                .put("type", "json_schema")
                .put("name", "unbiased_post_draft")
                .put("strict", true);
        format.set("schema", schema());
        root.set("text", objectMapper.createObjectNode().set("format", format));
        return root;
    }

    private ArrayNode input(String request) {
        return objectMapper.createArrayNode()
                .add(message("system", SYSTEM_PROMPT))
                .add(message("user", request.trim()));
    }

    private ObjectNode message(String role, String content) {
        return objectMapper.createObjectNode().put("role", role).put("content", content);
    }

    private ArrayNode tools() {
        return objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("type", "web_search"));
    }

    private ObjectNode schema() {
        ObjectNode claimProperties = objectMapper.createObjectNode();
        claimProperties.set("text", stringType(40, 700));
        claimProperties.set("sourceUrls", arrayOf(uriType(), 1, 6));
        ObjectNode claim = objectType();
        claim.set("properties", claimProperties);
        claim.set("required", required("text", "sourceUrls"));

        ObjectNode sourceProperties = objectMapper.createObjectNode();
        sourceProperties.set("url", uriType());
        sourceProperties.set("title", stringType(1, 300));
        sourceProperties.set("publisher", stringType(1, 160));
        ObjectNode source = objectType();
        source.set("properties", sourceProperties);
        source.set("required", required("url", "title", "publisher"));

        ObjectNode properties = objectMapper.createObjectNode();
        properties.set("summaryClaims", arrayOf(claim, 1, 8));
        properties.set("caseForClaims", arrayOf(claim, 1, 6));
        properties.set("caseAgainstClaims", arrayOf(claim, 1, 6));
        properties.set("supportQuestion", stringType(20, 240));
        properties.set("sources", arrayOf(source, 2, 16));
        properties.set("imageBrief", stringType(20, 500));
        properties.set("imageSearchQuery", stringType(5, 200));

        ObjectNode schema = objectType();
        schema.set("properties", properties);
        schema.set("required", required(
                "summaryClaims", "caseForClaims", "caseAgainstClaims", "supportQuestion",
                "sources", "imageBrief", "imageSearchQuery"));
        return schema;
    }

    private ObjectNode objectType() {
        return objectMapper.createObjectNode()
                .put("type", "object")
                .put("additionalProperties", false);
    }

    private ObjectNode stringType(int minLength, int maxLength) {
        return objectMapper.createObjectNode()
                .put("type", "string")
                .put("minLength", minLength)
                .put("maxLength", maxLength);
    }

    private ObjectNode uriType() {
        return objectMapper.createObjectNode()
                .put("type", "string")
                .put("format", "uri");
    }

    private ObjectNode arrayOf(JsonNode items, int minItems, int maxItems) {
        ObjectNode array = objectMapper.createObjectNode()
                .put("type", "array")
                .put("minItems", minItems)
                .put("maxItems", maxItems);
        array.set("items", items);
        return array;
    }

    private ArrayNode required(String... fields) {
        ArrayNode required = objectMapper.createArrayNode();
        for (String field : fields) {
            required.add(field);
        }
        return required;
    }
}
