package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.error.UnwrappedApiException;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The two Post Unwrapped feature flags (ADR-054).
 *
 * <p>{@code enabled} is the kill switch: when false no Unwrapped content is generated, queued or
 * served. {@code unwrapButton} only changes the app: when true a vote leads to the results with an
 * Unwrap button instead of straight into Unwrapped. Both are exported as gauges so a dashboard
 * shows why generation traffic stopped.</p>
 */
@ApplicationScoped
public class UnwrappedFeatureFlags {
    @ConfigProperty(name = "unwrapped.features.enabled", defaultValue = "true")
    boolean enabled;
    @ConfigProperty(name = "unwrapped.features.unwrap-button", defaultValue = "true")
    boolean unwrapButton;
    @ConfigProperty(name = "app.environment")
    String environment;
    @Inject
    MeterRegistry registry;

    void exportFlagGauges(@Observes StartupEvent event) {
        registerFlagGauge("enabled", enabled);
        registerFlagGauge("unwrap_button", unwrapButton);
    }

    private void registerFlagGauge(String flag, boolean value) {
        Gauge.builder("yoursay.unwrapped.feature.enabled", () -> value ? 1 : 0)
                .tag("flag", flag)
                .tag("environment", environment)
                .register(registry);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean unwrapButton() {
        return unwrapButton;
    }

    public void assertEnabled() {
        if (!enabled) {
            throw UnwrappedApiException.disabled();
        }
    }
}
