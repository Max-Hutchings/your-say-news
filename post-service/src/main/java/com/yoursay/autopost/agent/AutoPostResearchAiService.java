package com.yoursay.autopost.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        modelName = "autopost",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
interface AutoPostResearchAiService {

    @SystemMessage("""
            {{systemPrompt}}

            {{outputInstructions}}
            """)
    StoryDiscoveryDraft discover(
            @V("systemPrompt") String systemPrompt,
            @V("outputInstructions") String outputInstructions,
            @UserMessage String windowInstruction);
}
