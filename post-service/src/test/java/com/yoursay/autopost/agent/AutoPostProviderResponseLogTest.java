package com.yoursay.autopost.agent;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPostProviderResponseLogTest {

    @Test
    void logsTheCompleteProviderResponseWithFaultAndTraceCorrelation() {
        String rawResponse = """
                {"id":"response-42","output":[{"type":"message","status":"completed",
                 "content":[{"type":"output_text","text":"complete generated response"}]}],
                 "usage":{"num_server_side_tools_used":0,"num_sources_used":0}}
                """.strip();
        String traceId = "0123456789abcdef0123456789abcdef";
        SpanContext context = SpanContext.create(
                traceId,
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());

        LogRecord record;
        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            record = captureLog(() -> new AutoPostProviderResponseLog().missingWebSearch(rawResponse));
        }

        assertEquals(Level.WARNING, record.getLevel());
        assertTrue(record.getMessage().contains("domain=autopost operation=providerResearch outcome=fault"));
        assertTrue(record.getMessage().contains("event=provider_response_body stage=web_search"));
        assertTrue(record.getMessage().contains("fault_type=provider_contract"));
        assertTrue(record.getMessage().contains("fault_code=AUTO_POST_WEB_SEARCH_MISSING"));
        assertTrue(record.getMessage().contains("trace_id=" + traceId));
        assertTrue(record.getMessage().endsWith("provider_response=" + rawResponse));
    }

    @Test
    void logsWithoutTraceCorrelationWhenNoTraceIsActive() {
        LogRecord record = captureLog(() ->
                new AutoPostProviderResponseLog().missingWebSearch("{\"output\":[]}"));

        assertTrue(record.getMessage().contains("trace_id=none"));
        assertTrue(record.getMessage().endsWith("provider_response={\"output\":[]}"));
    }

    private static LogRecord captureLog(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        assertEquals(1, logs.matchCount);
        assertNotNull(logs.providerResponse);
        return logs.providerResponse;
    }

    private static final class CapturingLogHandler extends Handler {
        private LogRecord providerResponse;
        private int matchCount;

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().contains("event=provider_response_body")) {
                matchCount++;
                providerResponse = record;
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
