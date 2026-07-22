package com.yoursay.agents.postagent.generator;

import com.yoursay.agents.postagent.AgentDraftDto;

import java.util.List;

record PepperAiResponse(
        AgentDraftDto draft,
        List<String> citations,
        String model,
        String providerResponseId
) {
}
