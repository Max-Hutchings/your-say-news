package com.yoursay.autopost.agent;

import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@ApplicationScoped
class AutoPostAiClient {

    @Inject
    AutoPostResearchAiService service;

    @Inject
    AutoPostChatResponseCapture responseCapture;

    @Inject
    AutoPostProviderResponseInspector responseInspector;

    @ConfigProperty(name = "autopost.agent.model", defaultValue = "configured-model")
    String configuredModel;

    StoryDiscoveryResult discover(Instant windowStart, Instant windowEnd) {
        String instruction = """
                Current UTC time: %s
                Treat this current time as authoritative. The supplied window is not future-dated.
                Window start inclusive: %s
                Window end inclusive: %s
                Use live web search now before returning the ten stories.
                """.formatted(windowEnd, windowStart, windowEnd).strip();
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
            responseInspector.requireCompletedWebSearch(response);
            return new StoryDiscoveryResult(
                    mapStories(draft.stories()),
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
        return new AutoPostDiscoveryException(
                "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                "structured_output_mapping",
                message,
                false,
                null);
    }

    private static List<DiscoveredStory> mapStories(List<DiscoveredStoryDraft> stories) {
        if (stories == null) {
            return null;
        }
        return stories.stream().map(AutoPostAiClient::mapStory).toList();
    }

    private static DiscoveredStory mapStory(DiscoveredStoryDraft story) {
        if (story == null) {
            return null;
        }
        return new DiscoveredStory(
                story.rank(),
                story.region(),
                story.headline(),
                story.summary(),
                story.deduplicationKey(),
                parsePublishedAt(story.publishedAt()),
                story.sources());
    }

    private static Instant parsePublishedAt(String publishedAt) {
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(publishedAt);
        } catch (DateTimeParseException error) {
            throw new AutoPostDiscoveryException(
                    "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                    "structured_output_mapping",
                    "The model returned an invalid publishedAt timestamp",
                    false,
                    error);
        }
    }
}
