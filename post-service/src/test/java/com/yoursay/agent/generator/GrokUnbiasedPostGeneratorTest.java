package com.yoursay.agent.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agent.client.XaiResponsesClient;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrokUnbiasedPostGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private XaiResponsesClient client;
    private GrokUnbiasedPostGenerator generator;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(XaiResponsesClient.class);
        generator = new GrokUnbiasedPostGenerator();
        generator.client = client;
        generator.promptFactory = new GrokPromptFactory(objectMapper);
        generator.validator = new AgentDraftValidator();
        generator.objectMapper = objectMapper;
        generator.apiKey = "xai-test-key";
        generator.model = "grok-4.3";
        generator.reasoningEffort = "low";
        generator.maxOutputTokens = 4000;
    }

    @Test
    void generateReturnsStructuredClaimSourceMappingsAndSendsSearchSchema() throws Exception {
        Mockito.when(client.createResponse(Mockito.anyString(), Mockito.any()))
                .thenReturn(Response.ok(providerResponse(validDraftJson(
                        "https://www.ons.gov.uk/releases/electiondata",
                        "https://www.parliament.uk/bills/voting-age"))).build());

        GenerationResult result = generator.generate(
                "Explain the proposal to lower the UK voting age and the strongest arguments.");

        assertEquals("grok-4.3", result.model());
        assertEquals("resp_uk_voting_age", result.providerResponseId());
        assertEquals("Should the UK voting age be lowered to 16?", result.draft().supportQuestion());
        assertEquals(
                "The proposal would extend voting rights to 16- and 17-year-olds.",
                result.draft().summaryClaims().getFirst().text());
        assertEquals(
                "https://www.parliament.uk/bills/voting-age",
                result.draft().caseForClaims().getFirst().sourceUrls().getFirst());
        assertEquals(2, result.draft().sources().size());

        ArgumentCaptor<JsonNode> request = ArgumentCaptor.forClass(JsonNode.class);
        Mockito.verify(client).createResponse(Mockito.eq("Bearer xai-test-key"), request.capture());
        JsonNode sent = request.getValue();
        assertEquals("grok-4.3", sent.path("model").asText());
        assertEquals("low", sent.path("reasoning").path("effort").asText());
        assertEquals("web_search", sent.path("tools").get(0).path("type").asText());
        assertTrue(sent.path("text").path("format").path("strict").asBoolean());
        assertEquals("uri", sent.path("text").path("format").path("schema")
                .path("properties").path("sources").path("items")
                .path("properties").path("url").path("format").asText());
        assertEquals("no_inline_citations", sent.path("include").get(0).asText());
    }

    @Test
    void generateRejectsClaimUrlThatGrokDidNotReturnAsASearchCitation() throws Exception {
        String official = "https://www.ons.gov.uk/releases/electiondata";
        String parliament = "https://www.parliament.uk/bills/voting-age";
        String fabricated = "https://fabricated.example/evidence";
        String draft = validDraftJson(official, parliament).replace(parliament, fabricated);

        JsonNode response = providerResponse(draft);
        ((com.fasterxml.jackson.databind.node.ArrayNode) response.path("citations"))
                .removeAll().add(official).add(parliament);
        Mockito.when(client.createResponse(Mockito.anyString(), Mockito.any()))
                .thenReturn(Response.ok(response).build());

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover voting age."));

        assertEquals("AGENT_INVALID_PROVIDER_OUTPUT", error.code());
        assertFalse(error.retryable());
        assertTrue(error.getMessage().contains("not returned in provider citations"));
    }

    @Test
    void generateMarksRateLimitAsRetryableWithoutLeakingProviderBody() {
        Mockito.when(client.createResponse(Mockito.anyString(), Mockito.any()))
                .thenReturn(Response.status(429)
                        .entity(objectMapper.createObjectNode().put("error", "account-specific detail"))
                        .build());

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_HTTP_429", error.code());
        assertTrue(error.retryable());
        assertEquals("xAI response failed with status 429", error.getMessage());
    }

    @Test
    void generateMarksServerFailureAsRetryableAndOrdinaryClientFailureAsFinal() {
        Mockito.when(client.createResponse(Mockito.anyString(), Mockito.any()))
                .thenReturn(Response.status(503).build())
                .thenReturn(Response.status(400).build());

        GenerationException serverError = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));
        GenerationException clientError = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_HTTP_503", serverError.code());
        assertTrue(serverError.retryable());
        assertEquals("AGENT_PROVIDER_HTTP_400", clientError.code());
        assertFalse(clientError.retryable());
    }

    private JsonNode providerResponse(String draftJson) throws Exception {
        return objectMapper.readTree("""
                {
                  "id": "resp_uk_voting_age",
                  "model": "grok-4.3",
                  "citations": [
                    "https://www.ons.gov.uk/releases/electiondata",
                    "https://www.parliament.uk/bills/voting-age"
                  ],
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        { "type": "output_text", "text": %s }
                      ]
                    }
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(draftJson)));
    }

    private static String validDraftJson(String statisticsUrl, String parliamentUrl) {
        return """
                {
                  "summaryClaims": [
                    {
                      "text": "The proposal would extend voting rights to 16- and 17-year-olds.",
                      "sourceUrls": ["%s", "%s"]
                    }
                  ],
                  "caseForClaims": [
                    {
                      "text": "Supporters argue that people affected by public policy should gain a vote earlier.",
                      "sourceUrls": ["%s"]
                    }
                  ],
                  "caseAgainstClaims": [
                    {
                      "text": "Opponents argue that the existing age threshold remains a clearer national standard.",
                      "sourceUrls": ["%s"]
                    }
                  ],
                  "supportQuestion": "Should the UK voting age be lowered to 16?",
                  "sources": [
                    {
                      "url": "%s",
                      "title": "Election participation data",
                      "publisher": "Office for National Statistics"
                    },
                    {
                      "url": "%s",
                      "title": "Voting age bill",
                      "publisher": "UK Parliament"
                    }
                  ],
                  "imageBrief": "A neutral photograph of a polling station sign without identifiable voters.",
                  "imageSearchQuery": "UK polling station sign reusable licensed image"
                }
                """.formatted(statisticsUrl, parliamentUrl, parliamentUrl, statisticsUrl,
                statisticsUrl, parliamentUrl);
    }
}
