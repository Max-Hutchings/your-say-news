package com.yoursay.posts.postagent.generator;

import com.yoursay.posts.postagent.dto.AgentDraftDto;

import java.util.List;

record PepperAiResponse(
        AgentDraftDto draft,
        List<String> citations,
        String model,
        String providerResponseId
) {
}
