package com.yoursay.autopost.agent;

import com.yoursay.platform.ai.AiConfig;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    @Inject
    AiConfig aiConfig;

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
            StoryDiscoveryDraft draft;
            try {
                draft = service.discover(
                        AutoPostSystemPrompt.DEFAULT,
                        AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                        instruction);
            } catch (RuntimeException error) {
                throw classifyIncompleteResponse(responseCapture.take(), error);
            }
            ChatResponse response = responseCapture.take();
            if (draft == null) {
                throw invalid("The model returned no discovery response");
            }
            responseInspector.requireCompletedWebSearch(response);
            return new StoryDiscoveryResult(
                    mapStories(draft.stories()),
                    response == null
                            ? aiConfig.autoPost().model()
                            : valueOr(response.modelName(), aiConfig.autoPost().model()),
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

    private static RuntimeException classifyIncompleteResponse(
            ChatResponse response,
            RuntimeException error
    ) {
        if (response != null && response.finishReason() == FinishReason.LENGTH) {
            return new AutoPostDiscoveryException(
                    "AUTO_POST_MODEL_RESPONSE_TOO_LARGE",
                    "provider_contract",
                    "structured_output_truncated",
                    "The model response was too large and was rejected. Try a new run.",
                    false,
                    error);
        }
        return error;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
