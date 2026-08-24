package com.yoursay.autopost.agent;

import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.observability.DomainMetrics;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RetriableException;
import dev.langchain4j.service.output.OutputParsingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;

@ApplicationScoped
public class LangChain4jStoryDiscoveryAgent implements StoryDiscoveryAgent {

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
        AutoPostLog.started("providerResearch", "provider_request");
        if (apiKey == null || apiKey.isBlank() || API_KEY_NOT_CONFIGURED.equals(apiKey)) {
            record("fault", "configuration", "AUTO_POST_PROVIDER_NOT_CONFIGURED", started);
            AutoPostDiscoveryException fault = new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_NOT_CONFIGURED",
                    "configuration",
                    "provider_configuration",
                    "Auto-post discovery is not configured",
                    false,
                    null);
            AutoPostLog.failed("providerResearch", fault.stage(), fault.faultType(),
                    fault.code(), fault);
            throw fault;
        }
        try {
            StoryDiscoveryResult result = client.discover(windowStart, windowEnd);
            record("success", "none", "none", started);
            AutoPostLog.succeeded("providerResearch", "structured_output_mapping");
            return result;
        } catch (AutoPostDiscoveryException error) {
            record("fault", error.faultType(), error.code(), started);
            AutoPostLog.failed("providerResearch", error.stage(), error.faultType(),
                    error.code(), error);
            throw error;
        } catch (OutputParsingException error) {
            record("fault", "provider_contract", "AUTO_POST_PROVIDER_RESPONSE_INVALID", started);
            AutoPostDiscoveryException fault = new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                    "structured_output_parsing",
                    "Auto-post discovery provider returned invalid structured output",
                    false,
                    error);
            AutoPostLog.failed("providerResearch", fault.stage(), fault.faultType(),
                    fault.code(), fault);
            throw fault;
        } catch (RetriableException error) {
            record("fault", "dependency", "AUTO_POST_PROVIDER_UNAVAILABLE", started);
            AutoPostDiscoveryException fault = new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_UNAVAILABLE", "dependency", "provider_request",
                    "Auto-post discovery provider is unavailable", true, error);
            AutoPostLog.failed("providerResearch", fault.stage(), fault.faultType(),
                    fault.code(), fault);
            throw fault;
        } catch (NonRetriableException error) {
            record("fault", "provider_contract", "AUTO_POST_PROVIDER_RESPONSE_INVALID", started);
            AutoPostDiscoveryException fault = new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_RESPONSE_INVALID", "provider_contract", "provider_response",
                    "Auto-post discovery provider returned invalid output", false, error);
            AutoPostLog.failed("providerResearch", fault.stage(), fault.faultType(),
                    fault.code(), fault);
            throw fault;
        } catch (RuntimeException error) {
            record("fault", "application", "AUTO_POST_PROVIDER_PROCESSING_FAILED", started);
            AutoPostDiscoveryException fault = new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_PROCESSING_FAILED", "application", "provider_response",
                    "Auto-post discovery response processing failed", false, error);
            AutoPostLog.failed("providerResearch", fault.stage(), fault.faultType(),
                    fault.code(), fault);
            throw fault;
        }
    }

    private void record(String outcome, String type, String code, long started) {
        metrics.recordOperation("autopost", "providerResearch", outcome, type, code,
                System.nanoTime() - started);
    }
}
