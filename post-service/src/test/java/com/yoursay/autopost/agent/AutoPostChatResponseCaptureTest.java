package com.yoursay.autopost.agent;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoPostChatResponseCaptureTest {

    @Test
    void capturesOnlyTheResponseReceivedWhileActiveAndClearsAfterTake() {
        AutoPostChatResponseCapture capture = new AutoPostChatResponseCapture();
        ChatResponse ignored = response("ignored");
        capture.onResponse(context(ignored));
        assertNull(capture.take());

        ChatResponse expected = response("response-42");
        capture.begin();
        capture.onResponse(context(expected));
        assertEquals(expected, capture.take());

        capture.onResponse(context(response("stale")));
        assertNull(capture.take());
    }

    private static ChatModelResponseContext context(ChatResponse response) {
        return new ChatModelResponseContext(response, Mockito.mock(ChatRequest.class),
                ModelProvider.OPEN_AI, new HashMap<>());
    }

    private static ChatResponse response(String id) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(ChatResponseMetadata.builder().id(id).build())
                .build();
    }
}
