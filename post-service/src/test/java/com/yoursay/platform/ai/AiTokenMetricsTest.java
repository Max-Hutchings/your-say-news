package com.yoursay.platform.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiTokenMetricsTest {

    @Test
    void recordsSystemTotalsAndPerAgentInputAndOutputTokens() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiTokenMetrics metrics = new AiTokenMetrics(registry, "test");

        metrics.record(AiAgentType.UNWRAPPED, "gpt-5.6-sol", 1_240, 318);
        metrics.record(AiAgentType.POST_AGENT, "gpt-5.6-sol", 820, 190);
        metrics.record(AiAgentType.AUTO_POST, "gpt-5.6-sol", 2_400, 740);

        assertEquals(1_240, tokenCount(registry, "unwrapped", "input"));
        assertEquals(318, tokenCount(registry, "unwrapped", "output"));
        assertEquals(820, tokenCount(registry, "postagent", "input"));
        assertEquals(190, tokenCount(registry, "postagent", "output"));
        assertEquals(2_400, tokenCount(registry, "autopost", "input"));
        assertEquals(740, tokenCount(registry, "autopost", "output"));

        assertEquals(4_460, totalByType(registry, "input"));
        assertEquals(1_248, totalByType(registry, "output"));
    }

    private static double tokenCount(
            SimpleMeterRegistry registry,
            String agentType,
            String tokenType
    ) {
        return registry.get("yoursay.ai.tokens.total")
                .tags("agent_type", agentType, "token_type", tokenType,
                        "model", "gpt-5.6-sol", "environment", "test")
                .counter()
                .count();
    }

    private static double totalByType(SimpleMeterRegistry registry, String tokenType) {
        return registry.find("yoursay.ai.tokens.total")
                .tag("token_type", tokenType)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
