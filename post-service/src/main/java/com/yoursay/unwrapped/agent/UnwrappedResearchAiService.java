package com.yoursay.unwrapped.agent;

import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        modelName = "unwrapped",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
interface UnwrappedResearchAiService {
    @SystemMessage(UnwrappedSystemPrompt.DEFAULT)
    UnwrappedResearchDraftV1 research(@UserMessage String brief);

    @SystemMessage("{{benchmarkSystemPrompt}}")
    UnwrappedResearchDraftV1 researchWithSystemPrompt(
            @V("benchmarkSystemPrompt") String systemPrompt,
            @UserMessage String brief);
}
