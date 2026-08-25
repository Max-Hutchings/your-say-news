package com.yoursay.platform.ai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiFailureResponseLogTest {

    @Test
    void logsTheCompleteRawProviderResponseWithFaultAndTraceCorrelation() {
        String rawResponse = """
                {"id":"response-42","output":[{"type":"message","status":"completed",
                 "content":[{"type":"output_text","text":"complete generated response"}]}]}
                """.strip();
        ChatResponse response = response(rawResponse);
        AiFailureResponseLog failureLog = new AiFailureResponseLog();
        failureLog.enabled = true;
        String traceId = "0123456789abcdef0123456789abcdef";
        SpanContext context = SpanContext.create(
                traceId, "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());

        LogRecord record;
        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            record = captureLog(() -> failureLog.log(
                    "unwrapped", "research_provider", "UNWRAPPED_DRAFT_MISSING", response));
        }

        assertEquals(Level.WARNING, record.getLevel());
        assertTrue(record.getMessage().contains(
                "domain=unwrapped operation=research_provider outcome=fault"));
        assertTrue(record.getMessage().contains("event=ai_failure_response"));
        assertTrue(record.getMessage().contains("fault_code=UNWRAPPED_DRAFT_MISSING"));
        assertTrue(record.getMessage().contains("trace_id=" + traceId));
        assertTrue(record.getMessage().endsWith("ai_response=" + rawResponse));
        assertEquals(rawResponse, failureLog.responseBody(response));
    }

    @Test
    void doesNotLogFullResponsesWhenDisabled() {
        AiFailureResponseLog failureLog = new AiFailureResponseLog();
        failureLog.enabled = false;

        assertNull(captureOptionalLog(() -> failureLog.log(
                "postagent", "generation", "AGENT_REQUIRED_FIELD", "full response")));
    }

    private static ChatResponse response(String body) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body)
                .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("structured fallback"))
                .metadata(OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("gpt-5.6-sol")
                        .rawHttpResponse(rawResponse)
                        .build())
                .build();
    }

    private static LogRecord captureLog(Runnable action) {
        LogRecord record = captureOptionalLog(action);
        assertNotNull(record);
        return record;
    }

    private static LogRecord captureOptionalLog(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        return logs.failureResponse;
    }

    private static final class CapturingLogHandler extends Handler {
        private LogRecord failureResponse;

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().contains("event=ai_failure_response")) {
                failureResponse = record;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
