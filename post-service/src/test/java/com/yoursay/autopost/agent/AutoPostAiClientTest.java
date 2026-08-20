package com.yoursay.autopost.agent;

import com.yoursay.autopost.AutoPostRegion;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPostAiClientTest {

    private AutoPostResearchAiService service;
    private AutoPostChatResponseCapture responseCapture;
    private AutoPostAiClient client;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AutoPostResearchAiService.class);
        responseCapture = Mockito.mock(AutoPostChatResponseCapture.class);
        client = new AutoPostAiClient();
        client.service = service;
        client.responseCapture = responseCapture;
        client.configuredModel = "configured-grok";
    }

    @Test
    void discoverUsesSeparatePromptsAndDirectStructuredOutput() {
        Instant start = Instant.parse("2026-08-19T12:00:00Z");
        Instant end = Instant.parse("2026-08-20T12:00:00Z");
        DiscoveredStory story = new DiscoveredStory(1, AutoPostRegion.UK, "Headline", "Summary",
                "story-key", end, List.of(new DiscoveredStorySource("url", "title", "publisher")));
        String instruction = "Window start inclusive: %s%nWindow end inclusive: %s".formatted(start, end);
        Mockito.when(service.discover(
                        AutoPostSystemPrompt.DEFAULT,
                        AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                        instruction))
                .thenReturn(new StoryDiscoveryDraft(List.of(story)));
        Mockito.when(responseCapture.take()).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("grok-4.5")
                        .build())
                .build());

        StoryDiscoveryResult result = client.discover(start, end);

        assertEquals(List.of(story), result.stories());
        assertEquals("grok-4.5", result.model());
        assertEquals("response-42", result.providerResponseId());
        assertEquals(List.of(), result.providerCitations());
        Mockito.verify(responseCapture).begin();
        Mockito.verify(service).discover(
                AutoPostSystemPrompt.DEFAULT,
                AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                instruction);
    }

    @Test
    void discoverUsesConfiguredModelWhenProviderMetadataIsUnavailable() {
        Instant start = Instant.parse("2026-08-19T12:00:00Z");
        Instant end = Instant.parse("2026-08-20T12:00:00Z");
        Mockito.when(service.discover(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new StoryDiscoveryDraft(List.of()));

        StoryDiscoveryResult result = client.discover(start, end);

        assertEquals("configured-grok", result.model());
        assertEquals(null, result.providerResponseId());
        Mockito.verify(responseCapture).clear();
    }

    @Test
    void discoverRejectsANullDraftAndAlwaysClearsResponseCapture() {
        Mockito.when(service.discover(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(null);

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> client.discover(
                        Instant.parse("2026-08-19T12:00:00Z"),
                        Instant.parse("2026-08-20T12:00:00Z")));

        assertEquals("AUTO_POST_PROVIDER_RESPONSE_INVALID", error.code());
        assertEquals("The model returned no discovery response", error.getMessage());
        Mockito.verify(responseCapture).clear();
    }
}
