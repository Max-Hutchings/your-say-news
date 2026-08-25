package com.yoursay.autopost.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPostProviderResponseInspectorTest {

    private AutoPostProviderResponseInspector inspector;
    @BeforeEach
    void setUp() {
        inspector = new AutoPostProviderResponseInspector();
        inspector.objectMapper = new ObjectMapper();
    }

    @Test
    void acceptsACompletedWebSearchCall() {
        ChatResponse response = responseWithOutput("""
                [{"type":"web_search_call","status":"completed"},
                 {"type":"message","status":"completed"}]
                """);

        assertDoesNotThrow(() -> inspector.requireCompletedWebSearch(response));
    }

    @Test
    void acceptsWhenAtLeastOneOfMultipleWebSearchCallsCompleted() {
        ChatResponse response = responseWithOutput("""
                [{"type":"web_search_call","status":"failed"},
                 {"type":"web_search_call","status":"completed"},
                 {"type":"message","status":"completed"}]
                """);

        assertDoesNotThrow(() -> inspector.requireCompletedWebSearch(response));
    }

    @Test
    void rejectsAResponseWithoutWebSearchEvidence() {
        String rawResponse = """
                {"id":"response-42","output":[{"type":"message","status":"completed"}],
                 "usage":{"num_server_side_tools_used":0,"num_sources_used":0}}
                """.strip();

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithBody(rawResponse)));

        assertEquals("AUTO_POST_WEB_SEARCH_MISSING", error.code());
        assertEquals("web_search", error.stage());
        assertEquals("provider_contract", error.faultType());
        assertFalse(error.retryable());
    }

    @Test
    void rejectsAWebSearchCallThatDidNotComplete() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithOutput("""
                        [{"type":"web_search_call","status":"failed"},
                         {"type":"message","status":"completed"}]
                        """)));

        assertEquals("AUTO_POST_WEB_SEARCH_FAILED", error.code());
        assertEquals("web_search", error.stage());
        assertEquals("dependency", error.faultType());
        assertFalse(error.retryable());
    }

    @Test
    void rejectsAbsentProviderEvidence() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(null));

        assertFailure(error, "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                "provider_contract", "web_search");
    }

    @Test
    void rejectsProviderEvidenceWithUnsupportedMetadata() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(ChatResponseMetadata.builder().id("response-42").build())
                .build();

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(response));

        assertFailure(error, "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                "provider_contract", "web_search");
    }

    @Test
    void rejectsProviderEvidenceWithoutRawHttpResponse() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("grok-4.5")
                        .build())
                .build();

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(response));

        assertFailure(error, "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                "provider_contract", "web_search");
    }

    @Test
    void rejectsBlankProviderEvidence() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithBody(" ")));

        assertFailure(error, "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                "provider_contract", "web_search");
    }

    @Test
    void rejectsMalformedProviderEvidence() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithBody("{not-json")));

        assertFailure(error, "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                "provider_contract", "provider_evidence");
        assertInstanceOf(JsonProcessingException.class, error.getCause());
    }

    @Test
    void rejectsProviderEvidenceWithoutAnOutputArray() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithBody("{\"output\":{}}")));

        assertFailure(error, "AUTO_POST_PROVIDER_EVIDENCE_MISSING",
                "provider_contract", "web_search");
    }

    private static void assertFailure(
            AutoPostDiscoveryException error,
            String code,
            String faultType,
            String stage
    ) {
        assertEquals(code, error.code());
        assertEquals(faultType, error.faultType());
        assertEquals(stage, error.stage());
        assertFalse(error.retryable());
    }

    private static ChatResponse responseWithOutput(String output) {
        return responseWithBody("{\"output\":" + output + "}");
    }

    private static ChatResponse responseWithBody(String body) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body)
                .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("grok-4.5")
                        .rawHttpResponse(rawResponse)
                        .build())
                .build();
    }
}
