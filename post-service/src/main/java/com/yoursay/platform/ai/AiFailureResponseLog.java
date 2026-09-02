package com.yoursay.platform.ai;

import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Logs complete failed AI responses only when explicitly enabled for local diagnostics. */
@ApplicationScoped
public class AiFailureResponseLog {

    @ConfigProperty(name = "agent.log-full-failed-response", defaultValue = "false")
    boolean enabled;

    public void log(
            String domain,
            String operation,
            String faultCode,
            ChatResponse response
    ) {
        log(domain, operation, faultCode, responseBody(response));
    }

    public void log(
            String domain,
            String operation,
            String faultCode,
            String response
    ) {
        if (!enabled || response == null || response.isBlank()) return;

        Log.warnf("domain=%s operation=%s outcome=fault event=ai_failure_response "
                        + "fault_code=%s trace_id=%s ai_response=%s",
                safeName(domain), safeName(operation), safeFaultCode(faultCode),
                traceId(), response);
    }

    public String responseBody(ChatResponse response) {
        if (response == null) return null;
        if (response.metadata() instanceof OpenAiResponsesChatResponseMetadata metadata) {
            SuccessfulHttpResponse rawResponse = metadata.rawHttpResponse();
            if (rawResponse != null && rawResponse.body() != null
                    && !rawResponse.body().isBlank()) {
                return rawResponse.body();
            }
        }
        return response.aiMessage() == null ? null : response.aiMessage().text();
    }

    private static String safeName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_]{1,80}")) return "unknown";
        return value;
    }

    private static String safeFaultCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,80}")) return "UNKNOWN";
        return value;
    }

    private static String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : "none";
    }
}
