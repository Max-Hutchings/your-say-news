package com.yoursay.platform.mobiletelemetry.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Posts OTLP/JSON to the collector's HTTP receiver: otel-lgtm on a laptop, Alloy on the dev server.
 * Sends are asynchronous so a slow collector never holds the app's upload request open. With no
 * endpoint configured (tests, unconfigured hosts) export is off and only metrics are recorded.
 */
@ApplicationScoped
class OtlpExporter {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MobileTelemetryMetrics metrics;

    @ConfigProperty(name = "mobile-telemetry.otlp.endpoint")
    Optional<URI> endpoint;

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    boolean isEnabled() {
        return endpoint.isPresent();
    }

    /** Completes once the collector answered or failed; callers do not wait on it. */
    CompletableFuture<Void> export(String signal, Map<String, Object> payload) {
        if (endpoint.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint.get().resolve("/v1/" + signal))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                    .build();
        } catch (JsonProcessingException e) {
            recordFailure(signal, "serialization_failed");
            return CompletableFuture.completedFuture(null);
        }
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .handle((response, failure) -> {
                    if (failure != null) {
                        recordFailure(signal, "collector_unreachable");
                    } else if (response.statusCode() >= 300) {
                        recordFailure(signal, "collector_http_" + response.statusCode());
                    }
                    return null;
                });
    }

    private void recordFailure(String signal, String faultCode) {
        metrics.recordExportFailure(signal);
        Log.warnf("domain=platform operation=export_mobile_telemetry outcome=fault fault_code=%s signal=%s",
                faultCode, signal);
    }
}
