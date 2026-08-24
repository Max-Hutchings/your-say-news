package com.yoursay.autopost.agent;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

/** Temporarily logs the complete provider body when required web-search evidence is absent. */
@ApplicationScoped
class AutoPostProviderResponseLog {

    void missingWebSearch(String providerResponse) {
        Log.warnf("domain=autopost operation=providerResearch outcome=fault "
                        + "event=provider_response_body stage=web_search "
                        + "fault_type=provider_contract fault_code=AUTO_POST_WEB_SEARCH_MISSING "
                        + "trace_id=%s provider_response=%s",
                traceId(), providerResponse);
    }

    private static String traceId() {
        SpanContext context = Span.current().getSpanContext();
        return context.isValid() ? context.getTraceId() : "none";
    }
}
