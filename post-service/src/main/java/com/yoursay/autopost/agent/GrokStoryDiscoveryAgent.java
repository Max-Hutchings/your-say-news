package com.yoursay.autopost.agent;

import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.yoursay.observability.DomainMetrics;

import java.time.Instant;

@ApplicationScoped
public class GrokStoryDiscoveryAgent implements StoryDiscoveryAgent {

    private static final String API_KEY_NOT_CONFIGURED = "__not_configured__";

    @Inject
    AutoPostAiClient client;

    @Inject
    DomainMetrics metrics;

    @ConfigProperty(name = "autopost.agent.api-key")
    String apiKey;

    @Override
    public StoryDiscoveryResult discover(Instant windowStart, Instant windowEnd) {
        long started = System.nanoTime();
        if (apiKey == null || apiKey.isBlank() || API_KEY_NOT_CONFIGURED.equals(apiKey)) {
            record("fault", "configuration", "AUTO_POST_PROVIDER_NOT_CONFIGURED", started);
            throw new AutoPostDiscoveryException("AUTO_POST_PROVIDER_NOT_CONFIGURED",
                    "Auto-post discovery is not configured", false);
        }
        try {
            StoryDiscoveryResult result = client.discover(windowStart, windowEnd);
            record("success", "none", "none", started);
            return result;
        } catch (AutoPostDiscoveryException error) {
            record("fault", "dependency", error.code(), started);
            throw error;
        } catch (RetriableException error) {
            record("fault", "dependency", "AUTO_POST_PROVIDER_UNAVAILABLE", started);
            throw new AutoPostDiscoveryException("AUTO_POST_PROVIDER_UNAVAILABLE",
                    "Auto-post discovery provider is unavailable", true, error);
        } catch (NonRetriableException error) {
            record("fault", "provider_contract", "AUTO_POST_PROVIDER_RESPONSE_INVALID", started);
            throw new AutoPostDiscoveryException("AUTO_POST_PROVIDER_RESPONSE_INVALID",
                    "Auto-post discovery provider returned invalid output", false, error);
        } catch (RuntimeException error) {
            record("fault", "dependency", "AUTO_POST_PROVIDER_UNAVAILABLE", started);
            throw new AutoPostDiscoveryException("AUTO_POST_PROVIDER_UNAVAILABLE",
                    "Auto-post discovery failed", true, error);
        }
    }

    private void record(String outcome, String type, String code, long started) {
        metrics.recordOperation("autopost", "providerResearch", outcome, type, code,
                System.nanoTime() - started);
    }
}
