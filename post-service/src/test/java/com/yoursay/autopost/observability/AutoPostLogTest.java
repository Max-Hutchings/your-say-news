package com.yoursay.autopost.observability;

import org.junit.jupiter.api.Test;

import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPostLogTest {

    @Test
    void failureLogDoesNotAttachOrRenderProviderExceptionDetails() {
        RuntimeException providerFailure = new RuntimeException(
                "api-key=sensitive-provider-detail",
                new IllegalArgumentException("secret-token-in-cause"));

        LogRecord record = captureFailure(() -> AutoPostLog.failed(
                "providerResearch",
                "provider_request",
                "dependency",
                "AUTOPOST_PROVIDER_FAILURE",
                providerFailure));

        assertNull(record.getThrown());
        assertEquals("domain=autopost operation=providerResearch outcome=fault "
                + "event=operation_failed stage=provider_request fault_type=dependency "
                + "fault_code=AUTOPOST_PROVIDER_FAILURE exception_type=RuntimeException "
                + "trace_id=none", record.getMessage());
        assertFalse(record.getMessage().contains("sensitive-provider-detail"));
        assertFalse(record.getMessage().contains("secret-token-in-cause"));
    }

    @Test
    void failureLogRecordsNoneWhenThereIsNoThrowable() {
        LogRecord record = captureFailure(() -> AutoPostLog.failed(
                "providerResearch",
                "provider_response",
                "provider_contract",
                "AUTOPOST_PROVIDER_RESPONSE_INVALID",
                null));

        assertNull(record.getThrown());
        assertTrue(record.getMessage().contains("exception_type=none"));
        assertTrue(record.getMessage().contains(
                "fault_code=AUTOPOST_PROVIDER_RESPONSE_INVALID"));
    }

    private static LogRecord captureFailure(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        assertNotNull(logs.failure);
        return logs.failure;
    }

    private static final class CapturingLogHandler extends Handler {
        private LogRecord failure;

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().contains("domain=autopost")
                    && record.getMessage().contains("outcome=fault")) {
                failure = record;
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
