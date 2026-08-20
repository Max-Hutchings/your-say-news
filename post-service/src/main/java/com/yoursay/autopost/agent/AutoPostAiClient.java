package com.yoursay.autopost.agent;

import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
class AutoPostAiClient {

    @Inject
    AutoPostResearchAiService service;

    @Inject
    AutoPostChatResponseCapture responseCapture;

    @ConfigProperty(name = "autopost.agent.model", defaultValue = "configured-model")
    String configuredModel;

    StoryDiscoveryResult discover(Instant windowStart, Instant windowEnd) {
        String instruction = "Window start inclusive: %s%nWindow end inclusive: %s"
                .formatted(windowStart, windowEnd);
        responseCapture.begin();
        try {
            StoryDiscoveryDraft draft = service.discover(
                    AutoPostSystemPrompt.DEFAULT,
                    AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                    instruction);
            ChatResponse response = responseCapture.take();
            if (draft == null) {
                throw invalid("The model returned no discovery response");
            }
            return new StoryDiscoveryResult(
                    draft.stories(),
                    response == null || response.modelName() == null
                            ? configuredModel
                            : response.modelName(),
                    response == null ? null : response.id(),
                    List.of());
        } finally {
            responseCapture.clear();
        }
    }

    private static AutoPostDiscoveryException invalid(String message) {
        return new AutoPostDiscoveryException("AUTO_POST_PROVIDER_RESPONSE_INVALID", message, false);
    }
}
