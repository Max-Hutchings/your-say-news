package com.yoursay.agent.generator;

import com.yoursay.agent.AgentDraftDto;

public record GenerationResult(
        AgentDraftDto draft,
        String model,
        String providerResponseId
) {
}
