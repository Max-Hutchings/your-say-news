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
    @SystemMessage("""
            {{systemPrompt}}

            {{outputInstructions}}
            """)
    UnwrappedResearchDraftV1 research(
            @V("systemPrompt") String systemPrompt,
            @V("outputInstructions") String outputInstructions,
            @UserMessage String input);
}
