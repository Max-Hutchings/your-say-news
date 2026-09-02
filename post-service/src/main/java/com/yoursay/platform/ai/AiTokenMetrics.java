package com.yoursay.platform.ai;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Counts provider-reported tokens with stable labels and no request or user identifiers. */
@ApplicationScoped
public class AiTokenMetrics {

    private final MeterRegistry registry;
    private final String environment;

    @Inject
    public AiTokenMetrics(
            MeterRegistry registry,
            @ConfigProperty(name = "quarkus.profile", defaultValue = "prod") String environment
    ) {
        this.registry = registry;
        this.environment = environment;
    }

    public void record(AiAgentType agentType, ChatResponse response) {
        if (response == null || response.tokenUsage() == null) {
            return;
        }
        TokenUsage usage = response.tokenUsage();
        record(agentType, response.modelName(),
                count(usage.inputTokenCount()), count(usage.outputTokenCount()));
    }

    void record(AiAgentType agentType, String model, long inputTokens, long outputTokens) {
        recordTokenType(agentType, "input", model, inputTokens);
        recordTokenType(agentType, "output", model, outputTokens);
    }

    private void recordTokenType(
            AiAgentType agentType,
            String tokenType,
            String model,
            long tokens
    ) {
        if (tokens <= 0) {
            return;
        }
        Tags tags = Tags.of(
                "agent_type", agentType.metricValue(),
                "token_type", tokenType,
                "model", safeModel(model),
                "environment", environment
        );
        registry.counter("yoursay.ai.tokens.total", tags).increment(tokens);
    }

    private static long count(Integer value) {
        return value == null ? 0 : value.longValue();
    }

    private static String safeModel(String model) {
        if (model == null || !model.matches("[A-Za-z0-9._:-]{1,80}")) {
            return "unknown";
        }
        return model;
    }
}
