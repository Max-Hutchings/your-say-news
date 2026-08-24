package com.yoursay.posts.postagent.generator;

import com.yoursay.posts.postagent.dto.AgentDraftDto;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        modelName = "pepper",
        chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class
)
public interface PepperAiService {

    @SystemMessage("""
            {{systemPrompt}}

            {{outputInstructions}}
            """)
    Result<AgentDraftDto> research(
            @V("systemPrompt") String systemPrompt,
            @V("outputInstructions") String outputInstructions,
            @UserMessage String request);
}
