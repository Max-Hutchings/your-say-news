package com.yoursay.autopost.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.quarkus.logging.Log;

/** Writes bounded, trace-correlated workflow checkpoints for the auto-post domain. */
public final class AutoPostLog {

    public static void started(String operation, String stage) {
        Log.infof("domain=autopost operation=%s outcome=started stage=%s trace_id=%s",
                operation, stage, traceId());
    }

    public static void succeeded(String operation, String stage) {
        Log.infof("domain=autopost operation=%s outcome=success stage=%s trace_id=%s",
                operation, stage, traceId());
    }

    public static void failed(
            String operation,
            String stage,
            String faultType,
            String faultCode,
            Throwable fault
    ) {
        String message = "domain=autopost operation=%s outcome=fault event=operation_failed "
                + "stage=%s fault_type=%s fault_code=%s trace_id=%s";
        if (fault == null) {
            Log.warnf(message, operation, stage, faultType, faultCode, traceId());
            return;
        }
        Log.warnf(fault,
                message,
                operation, stage, faultType, faultCode, traceId());
    }

    public static void rejected(String operation, String stage, String errorCode) {
        Log.warnf("domain=autopost operation=%s outcome=error stage=%s error_type=workflow "
                        + "error_code=%s trace_id=%s",
                operation, stage, errorCode, traceId());
    }

    private static String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : "none";
    }

    private AutoPostLog() {
    }
}
