package com.yoursay.posts.postagent.agent;

import com.yoursay.posts.postagent.dto.AgentDraftDto;

import java.util.List;

record PepperAiResponse(
        AgentDraftDto draft,
        List<String> citations,
        String model,
        String providerResponseId,
        String rawResponse
) {
    PepperAiResponse(
            AgentDraftDto draft,
            List<String> citations,
            String model,
            String providerResponseId
    ) {
        this(draft, citations, model, providerResponseId, null);
    }
}
