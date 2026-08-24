package com.yoursay.platform.ai;

import dev.langchain4j.exception.HttpException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Optional;

/** Logs bounded diagnostics when an AI provider rejects an HTTP request. */
@ApplicationScoped
public class AiProviderFailureLog {

    public void logNonSuccessResponse(
            AiConfig.Provider provider,
            String domain,
            String operation,
            String faultCode,
            Throwable failure
    ) {
        nonSuccessResponseMessage(provider, domain, operation, faultCode, failure)
                .ifPresent(Log::warn);
    }

    static Optional<String> nonSuccessResponseMessage(
            AiConfig.Provider provider,
            String domain,
            String operation,
            String faultCode,
            Throwable failure
    ) {
        Integer status = providerHttpStatus(failure);
        if (status == null || status >= 200 && status < 300) return Optional.empty();

        return Optional.of("AI provider returned non-success response: domain=" + domain
                + " operation=" + operation
                + " outcome=fault provider=" + provider.name().toLowerCase(Locale.ROOT)
                + " http_status=" + status
                + " fault_code=" + faultCode
                + " trace_id=" + traceId());
    }

    private static Integer providerHttpStatus(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof HttpException httpFailure) return httpFailure.statusCode();
            current = current.getCause();
        }
        return null;
    }

    private static String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : "none";
    }
}
