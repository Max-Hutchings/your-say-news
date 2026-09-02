package com.yoursay.autopost.agent;

import com.yoursay.autopost.AutoPostRegion;
import com.yoursay.platform.ai.AiConfig;
import com.yoursay.platform.ai.AiFailureResponseLog;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPostAiClientTest {

    private AutoPostResearchAiService service;
    private AutoPostChatResponseCapture responseCapture;
    private AutoPostProviderResponseInspector responseInspector;
    private AiFailureResponseLog failureResponseLog;
    private AutoPostAiClient client;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(AutoPostResearchAiService.class);
        responseCapture = Mockito.mock(AutoPostChatResponseCapture.class);
        responseInspector = Mockito.mock(AutoPostProviderResponseInspector.class);
        failureResponseLog = Mockito.mock(AiFailureResponseLog.class);
        client = new AutoPostAiClient();
        client.service = service;
        client.responseCapture = responseCapture;
        client.responseInspector = responseInspector;
        client.aiConfig = aiConfig("configured-grok");
        client.failureResponseLog = failureResponseLog;
    }

    @Test
    void discoveryContractDoesNotLetTheProviderDeclareOperationalFailure() {
        List<String> componentNames = Arrays.stream(StoryDiscoveryDraft.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        String systemPrompt = AutoPostSystemPrompt.DEFAULT.replaceAll("\\s+", " ");

        assertEquals(List.of("stories"), componentNames);
        assertEquals(false, AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS.contains("failureReason"));
        assertEquals(false, AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS.contains("`FAILED`"));
        assertEquals(false, systemPrompt.contains("first reported or officially announced"));
        assertEquals(true, systemPrompt.contains("reported in the last 24 hours"));
    }

    @Test
    void discoverUsesSeparatePromptsAndDirectStructuredOutput() {
        Instant start = Instant.parse("2026-08-19T12:00:00Z");
        Instant end = Instant.parse("2026-08-20T12:00:00Z");
        DiscoveredStory expectedStory = new DiscoveredStory(1, AutoPostRegion.UK, "Headline", "Summary",
                "story-key", end, List.of(new DiscoveredStorySource("url", "title", "publisher")));
        DiscoveredStoryDraft providerStory = new DiscoveredStoryDraft(
                1, AutoPostRegion.UK, "Headline", "Summary", "story-key", end.toString(),
                List.of(new DiscoveredStorySource("url", "title", "publisher")));
        String instruction = """
                Current UTC time: %s
                Treat this current time as authoritative. The supplied window is not future-dated.
                Window start inclusive: %s
                Window end inclusive: %s
                Use live web search now before returning the ten stories.
                """.formatted(end, start, end).strip();
        Mockito.when(service.discover(
                        AutoPostSystemPrompt.DEFAULT,
                        AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                        instruction))
                .thenReturn(new StoryDiscoveryDraft(List.of(providerStory)));
        ChatResponse providerResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("grok-4.5")
                        .build())
                .build();
        Mockito.when(responseCapture.take()).thenReturn(providerResponse);
        Mockito.when(failureResponseLog.responseBody(providerResponse))
                .thenReturn("complete provider response");

        StoryDiscoveryResult result = client.discover(start, end);

        assertEquals(List.of(expectedStory), result.stories());
        assertEquals("grok-4.5", result.model());
        assertEquals("response-42", result.providerResponseId());
        assertEquals(List.of(), result.providerCitations());
        assertEquals("complete provider response", result.rawProviderResponse());
        Mockito.verify(responseCapture).begin();
        Mockito.verify(service).discover(
                AutoPostSystemPrompt.DEFAULT,
                AutoPostSystemPrompt.OUTPUT_INSTRUCTIONS,
                instruction);
        Mockito.verify(responseInspector).requireCompletedWebSearch(providerResponse);
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
    void discoverUsesConfiguredModelWhenProviderMetadataHasABlankModel() {
        Instant start = Instant.parse("2026-08-19T12:00:00Z");
        Instant end = Instant.parse("2026-08-20T12:00:00Z");
        Mockito.when(service.discover(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new StoryDiscoveryDraft(List.of()));
        ChatResponse providerResponse = ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(ChatResponseMetadata.builder()
                        .id("response-with-blank-model")
                        .modelName("   ")
                        .build())
                .build();
        Mockito.when(responseCapture.take()).thenReturn(providerResponse);

        StoryDiscoveryResult result = client.discover(start, end);

        assertEquals("configured-grok", result.model());
        assertEquals("response-with-blank-model", result.providerResponseId());
        Mockito.verify(responseInspector).requireCompletedWebSearch(providerResponse);
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

    @Test
    void discoveryClassifiesOutputTruncatedByTheModelLimit() {
        OutputParsingException parsingFailure = new OutputParsingException(
                "Incomplete JSON", new IllegalArgumentException("truncated"));
        Mockito.when(service.discover(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(parsingFailure);
        Mockito.when(responseCapture.take()).thenReturn(ChatResponse.builder()
                .aiMessage(AiMessage.from("incomplete structured output"))
                .finishReason(FinishReason.LENGTH)
                .build());

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> client.discover(
                        Instant.parse("2026-08-19T12:00:00Z"),
                        Instant.parse("2026-08-20T12:00:00Z")));

        assertEquals("AUTO_POST_MODEL_RESPONSE_TOO_LARGE", error.code());
        assertEquals("structured_output_truncated", error.stage());
        assertEquals("The model response was too large and was rejected. Try a new run.",
                error.getMessage());
        Mockito.verify(responseCapture).clear();
    }

    private static AiConfig aiConfig(String autoPostModel) {
        return new AiConfig(
                "grok",
                "low",
                "pepper-key",
                "pepper-model",
                "test-replica",
                "autopost-key",
                autoPostModel,
                "top-stories-v3",
                "unwrapped-key",
                "unwrapped-model",
                false);
    }

    @Test
    void discoverRejectsANonIsoPublicationTimestampAtTheMappingStage() {
        DiscoveredStoryDraft providerStory = new DiscoveredStoryDraft(
                1, AutoPostRegion.GLOBAL, "Headline", "Summary", "story-key", "not-a-time",
                List.of(new DiscoveredStorySource("url", "title", "publisher")));
        Mockito.when(service.discover(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new StoryDiscoveryDraft(List.of(providerStory)));
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("full failed discovery response"))
                .build();
        Mockito.when(responseCapture.take()).thenReturn(response);

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> client.discover(
                        Instant.parse("2026-08-19T12:00:00Z"),
                        Instant.parse("2026-08-20T12:00:00Z")));

        assertEquals("AUTO_POST_PROVIDER_RESPONSE_INVALID", error.code());
        assertEquals("structured_output_mapping", error.stage());
        Mockito.verify(failureResponseLog).log(
                "autopost", "providerResearch", error.code(), response);
        Mockito.verify(responseCapture).clear();
    }
}
