package com.yoursay.autopost.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rejectsAResponseWithoutWebSearchEvidence() {
        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> inspector.requireCompletedWebSearch(responseWithOutput("""
                        [{"type":"message","status":"completed"}]
                        """)));

        assertEquals("AUTO_POST_WEB_SEARCH_MISSING", error.code());
        assertEquals("web_search", error.stage());
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
    }

    private static ChatResponse responseWithOutput(String output) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("{\"output\":" + output + "}")
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
