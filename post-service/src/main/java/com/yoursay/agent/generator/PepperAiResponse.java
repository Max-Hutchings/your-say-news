package com.yoursay.agent.generator;

import com.yoursay.agent.AgentDraftDto;

import java.util.List;

record PepperAiResponse(
        AgentDraftDto draft,
        List<String> citations,
        String model,
        String providerResponseId
) {
}
