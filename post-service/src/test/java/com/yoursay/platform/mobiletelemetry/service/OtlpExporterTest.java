package com.yoursay.platform.mobiletelemetry.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Runs against a real local HTTP server so the request the collector receives is what is asserted. */
class OtlpExporterTest {

    private HttpServer collector;
    private final AtomicReference<String> receivedPath = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private SimpleMeterRegistry registry;
    private OtlpExporter exporter;

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
            exchange.close();
        });
        collector.start();

        registry = new SimpleMeterRegistry();
        MobileTelemetryMetrics metrics = new MobileTelemetryMetrics();
        metrics.registry = registry;
        metrics.environment = "test";
        exporter = new OtlpExporter();
        exporter.objectMapper = new ObjectMapper();
        exporter.metrics = metrics;
        exporter.endpoint = Optional.of(URI.create("http://127.0.0.1:" + collector.getAddress().getPort()));
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void postsJsonToTheSignalPath() {
        exporter.export("logs", Map.of("resourceLogs", "payload")).join();

        assertEquals("/v1/logs", receivedPath.get());
        assertEquals("application/json", receivedContentType.get());
        assertEquals("{\"resourceLogs\":\"payload\"}", receivedBody.get());
        assertNull(registry.find("yoursay.mobile.telemetry.export.failures.total").counter());
    }

    @Test
    void collectorRejectionIsCountedAsAnExportFailure() {
        responseStatus.set(500);

        exporter.export("traces", Map.of()).join();

        assertEquals(1.0, failures("traces"));
    }

    @Test
    void unreachableCollectorIsCountedAsAnExportFailure() {
        collector.stop(0);

        exporter.export("logs", Map.of()).join();

        assertEquals(1.0, failures("logs"));
    }

    @Test
    void withoutAnEndpointNothingIsSent() {
        exporter.endpoint = Optional.empty();

        exporter.export("logs", Map.of()).join();

        assertNull(receivedPath.get());
        assertNull(registry.find("yoursay.mobile.telemetry.export.failures.total").counter());
    }

    private double failures(String signal) {
        return registry.get("yoursay.mobile.telemetry.export.failures.total")
                .tags("signal", signal, "environment", "test").counter().count();
    }
}
