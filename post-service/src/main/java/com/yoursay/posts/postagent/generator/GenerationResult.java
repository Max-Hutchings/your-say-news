package com.yoursay.posts.postagent.generator;

import com.yoursay.posts.postagent.dto.AgentDraftDto;

public record GenerationResult(
        AgentDraftDto draft,
        String model,
        String providerResponseId
) {
}
