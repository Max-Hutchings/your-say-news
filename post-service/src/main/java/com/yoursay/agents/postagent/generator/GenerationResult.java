package com.yoursay.agents.postagent.generator;

import com.yoursay.agents.postagent.AgentDraftDto;

public record GenerationResult(
        AgentDraftDto draft,
        String model,
        String providerResponseId
) {
}
