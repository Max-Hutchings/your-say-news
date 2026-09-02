package com.yoursay.platform.ai;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiProviderFailureLogTest {

    private final AiProviderFailureLog providerFailureLog = new AiProviderFailureLog();

    @Test
    void reportsProviderHttpStatusWithoutLeakingTheResponseMessage() {
        RuntimeException failure = new IllegalStateException(
                "UNWRAPPED_PROVIDER_REQUEST_INVALID",
                new InvalidRequestException(
                        new HttpException(400, "request body and provider response must stay private")));

        Optional<String> message = AiProviderFailureLog.nonSuccessResponseMessage(
                AiConfig.Provider.OPENAI,
                "unwrapped",
                "research_provider",
                "UNWRAPPED_PROVIDER_REQUEST_INVALID",
                failure);

        assertEquals(Optional.of(
                "AI provider returned non-success response: domain=unwrapped "
                        + "operation=research_provider outcome=fault provider=openai "
                        + "http_status=400 fault_code=UNWRAPPED_PROVIDER_REQUEST_INVALID "
                        + "trace_id=none"), message);
        assertFalse(message.orElseThrow().contains("request body"));

        List<LogRecord> warnings = captureWarnings(() -> providerFailureLog.logNonSuccessResponse(
                AiConfig.Provider.OPENAI,
                "unwrapped",
                "research_provider",
                "UNWRAPPED_PROVIDER_REQUEST_INVALID",
                failure));
        assertEquals(1, warnings.size());
        LogRecord warning = warnings.getFirst();
        assertEquals(Level.WARNING, warning.getLevel());
        assertEquals(message.orElseThrow(), warning.getMessage());
        assertNull(warning.getThrown());
    }

    @Test
    void logsOnlyOutsideTheCompleteSuccessfulHttpRange() {
        List<LogRecord> warnings = captureWarnings(() -> {
            providerFailureLog.logNonSuccessResponse(
                    AiConfig.Provider.OPENAI, "unwrapped", "research_provider",
                    "UNWRAPPED_PROVIDER_FAILURE", new HttpException(200, "successful"));
            providerFailureLog.logNonSuccessResponse(
                    AiConfig.Provider.OPENAI, "unwrapped", "research_provider",
                    "UNWRAPPED_PROVIDER_FAILURE", new HttpException(299, "successful boundary"));
            providerFailureLog.logNonSuccessResponse(
                    AiConfig.Provider.OPENAI, "unwrapped", "research_provider",
                    "UNWRAPPED_PROVIDER_FAILURE", new IllegalStateException("local failure"));
            providerFailureLog.logNonSuccessResponse(
                    AiConfig.Provider.OPENAI, "unwrapped", "research_provider",
                    "UNWRAPPED_PROVIDER_INFORMATIONAL", new HttpException(199, "informational"));
            providerFailureLog.logNonSuccessResponse(
                    AiConfig.Provider.OPENAI, "unwrapped", "research_provider",
                    "UNWRAPPED_PROVIDER_REDIRECT", new HttpException(300, "redirect"));
        });

        assertEquals(2, warnings.size());
        assertEquals("AI provider returned non-success response: domain=unwrapped "
                        + "operation=research_provider outcome=fault provider=openai "
                        + "http_status=199 fault_code=UNWRAPPED_PROVIDER_INFORMATIONAL trace_id=none",
                warnings.getFirst().getMessage());
        assertEquals("AI provider returned non-success response: domain=unwrapped "
                        + "operation=research_provider outcome=fault provider=openai "
                        + "http_status=300 fault_code=UNWRAPPED_PROVIDER_REDIRECT trace_id=none",
                warnings.getLast().getMessage());
    }

    @Test
    void correlatesProviderFailuresWithTheCurrentTrace() {
        String traceId = "0123456789abcdef0123456789abcdef";
        SpanContext context = SpanContext.create(
                traceId, "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault());

        Optional<String> message;
        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            message = AiProviderFailureLog.nonSuccessResponseMessage(
                    AiConfig.Provider.OPENAI, "postagent", "generation",
                    "AGENT_PROVIDER_UNAVAILABLE", new HttpException(503, "unavailable"));
        }

        assertEquals(Optional.of("AI provider returned non-success response: domain=postagent "
                + "operation=generation outcome=fault provider=openai http_status=503 "
                + "fault_code=AGENT_PROVIDER_UNAVAILABLE trace_id=" + traceId), message);
    }

    private static List<LogRecord> captureWarnings(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        return logs.warnings;
    }

    private static final class CapturingLogHandler extends Handler {
        private final List<LogRecord> warnings = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().startsWith("AI provider returned non-success response:")) {
                warnings.add(record);
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
