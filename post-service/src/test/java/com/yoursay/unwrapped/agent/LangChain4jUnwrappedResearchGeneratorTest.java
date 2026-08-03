package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.selection.CandidateRole;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import com.yoursay.votes.dto.CohortDimensionV1;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.control.ActivateRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LangChain4jUnwrappedResearchGeneratorTest {
    @Test
    void declaresRequestContextActivationForCdiCalls() {
        assertNotNull(LangChain4jUnwrappedResearchGenerator.class
                .getAnnotation(ActivateRequestContext.class));
    }

    @Test
    void developmentStubReturnsFixedDraftWithoutCallingTheProvider() {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        OptionBriefV1 option = mock(OptionBriefV1.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(option.option()).thenReturn(new VoteOptionDto(15L, "Agree", 0, "AGREE"));
        when(option.overallVoteCount()).thenReturn(377L);
        when(option.overallVotePercentage()).thenReturn(62.83);
        when(option.candidates()).thenReturn(List.of(cohort()));
        when(request.postId()).thenReturn(2005L);
        when(request.options()).thenReturn(List.of(option));

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.stubbed = true;

        UnwrappedResearchResult result = generator.generate(request);

        assertEquals("stubbed-unwrapped", result.model());
        assertEquals("stub-post-2005", result.providerResponseId());
        assertEquals(List.of(15L), result.draft().pages().stream()
                .map(page -> page.optionId()).toList());
        assertEquals("Why men favour the development option",
                result.draft().pages().getFirst().headline());
        assertEquals(List.of("https://www.ons.gov.uk/"), result.providerCitations());
        assertEquals(List.of("stub-source"), result.draft().sources().stream()
                .map(source -> source.id()).toList());
        assertEquals(List.of("stub-source"), result.draft().pages().getFirst()
                .paragraphs().getFirst().sourceIds());
        assertEquals(List.of("gender=MAN"), result.draft().pages().getFirst()
                .selectedCohortIds());
        verifyNoInteractions(aiService);
    }

    @Test
    void combinesStructuredDraftWithCapturedRawResponseMetadata() {
        UnwrappedResearchDraftV1 draft = mock(UnwrappedResearchDraftV1.class);
        UnwrappedResearchRequest request = representativeRequest();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/source");
        when(aiService.research(anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.validator = validator;
        generator.apiKey = "test-key";
        generator.configuredModel = "configured-fallback-model";

        UnwrappedResearchResult result = generator.generate(request);

        assertSame(draft, result.draft());
        assertEquals(List.of("https://www.ons.gov.uk/source"), result.providerCitations());
        assertEquals("provider-grok-4.5", result.model());
        assertEquals("response-42", result.providerResponseId());
        verify(validator).validate(request, draft, List.of("https://www.ons.gov.uk/source"));
        ArgumentCaptor<String> brief = ArgumentCaptor.forClass(String.class);
        verify(aiService).research(brief.capture());
        assertTrue(brief.getValue().contains("You must call web search before drafting any page."));
        assertTrue(brief.getValue().contains(
                "Write two or three paragraphs totalling 50 to 100 words"));
        assertTrue(brief.getValue().contains(
                "explain why the selected cohort, or voters choosing the option"));
        assertTrue(brief.getValue().contains(
                "When an option supplies cohort candidates"));
        assertTrue(brief.getValue().contains(
                "Give every paragraph one or more sourceIds"));
        assertTrue(brief.getValue().contains(
                "Include every referenced source exactly once in sources; empty sources are forbidden."));
        assertTrue(!brief.getValue().contains("Do not use these causal words anywhere"));
        assertTrue(brief.getValue().contains("Return exactly 2 pages."));
        assertTrue(brief.getValue().contains("[71, 72]"));
        assertTrue(brief.getValue().contains("\"postId\":42"));
        assertTrue(brief.getValue().contains("\"question\":\"Should the city introduce a workplace parking levy?\""));
        assertTrue(brief.getValue().contains("\"label\":\"Agree\""));
        assertTrue(brief.getValue().contains("\"label\":\"Disagree\""));
    }

    @Test
    void replacesOnlyTheSystemPromptForABenchmarkGeneration() {
        UnwrappedResearchDraftV1 draft = mock(UnwrappedResearchDraftV1.class);
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        when(aiService.researchWithSystemPrompt(anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.validator = validator;
        generator.apiKey = "test-key";
        generator.configuredModel = "grok-test";

        generator.generate(request, "Use a deliberately terse editorial voice.");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> brief = ArgumentCaptor.forClass(String.class);
        verify(aiService).researchWithSystemPrompt(systemPrompt.capture(), brief.capture());
        verifyNoMoreInteractions(aiService);
        assertEquals("Use a deliberately terse editorial voice.", systemPrompt.getValue());
        assertTrue(brief.getValue().contains("OUTPUT CONTRACT:"));
        assertTrue(brief.getValue().contains("You must call web search before drafting any page."));
        assertTrue(brief.getValue().contains("\"candidates\":[]"));
        assertTrue(brief.getValue().contains("do not invent a cohort"));
        verify(validator).validate(request, draft, List.of("https://www.ons.gov.uk/benchmark"));
    }

    @Test
    void classifiesANullStructuredDraftAsRetryableDraftFailure() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        when(aiService.researchWithSystemPrompt(anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return null;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.validator = validator;
        generator.apiKey = "test-key";
        generator.configuredModel = "configured-fallback-model";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request, "Write a concise researched comparison."));

        assertEquals("UNWRAPPED_DRAFT_MISSING", failure.getMessage());
        verifyNoInteractions(validator);
        verify(aiService).researchWithSystemPrompt(anyString(), anyString());
    }

    @Test
    void bindsTheExactProductionAndBenchmarkSystemMessages() throws Exception {
        SystemMessage production = UnwrappedResearchAiService.class
                .getDeclaredMethod("research", String.class)
                .getAnnotation(SystemMessage.class);
        var benchmarkMethod = UnwrappedResearchAiService.class
                .getDeclaredMethod("researchWithSystemPrompt", String.class, String.class);
        SystemMessage benchmark = benchmarkMethod.getAnnotation(SystemMessage.class);
        V promptVariable = benchmarkMethod.getParameters()[0].getAnnotation(V.class);

        assertArrayEquals(new String[]{UnwrappedSystemPrompt.DEFAULT}, production.value());
        assertArrayEquals(new String[]{"{{benchmarkSystemPrompt}}"}, benchmark.value());
        assertEquals("benchmarkSystemPrompt", promptVariable.value());
    }

    private static SelectedCohortV1 cohort() {
        return new SelectedCohortV1("gender=MAN",
                List.of(new CohortDimensionV1("gender", "MAN")),
                CandidateRole.CORE_ANCHOR, "Broad core group", 60, 60,
                45, 75, 75, 15, 30, 62, 84, 0.001, "Men");
    }

    private static UnwrappedResearchRequest representativeRequest() {
        OptionBriefV1 agree = new OptionBriefV1(
                new VoteOptionDto(71L, "Agree", 0, "AGREE"), 120, 60.0,
                List.of(cohort()), List.of("Explain the observed pattern."), null);
        OptionBriefV1 disagree = new OptionBriefV1(
                new VoteOptionDto(72L, "Disagree", 1, "DISAGREE"), 80, 40.0,
                List.of(cohort()), List.of("Explain the observed pattern."), null);
        return new UnwrappedResearchRequest(
                42L,
                "A city is considering a workplace parking levy.",
                "Should the city introduce a workplace parking levy?",
                "UNITED_KINGDOM",
                200,
                "aggregate-v1",
                List.of(agree, disagree));
    }

    private static UnwrappedResearchRequest requestWithoutCohorts() {
        OptionBriefV1 agree = new OptionBriefV1(
                new VoteOptionDto(71L, "Agree", 0, "AGREE"), 120, 60.0,
                List.of(), List.of("Write a general option argument."),
                "No reliable demographic concentration passes the narration rules.");
        OptionBriefV1 disagree = new OptionBriefV1(
                new VoteOptionDto(72L, "Disagree", 1, "DISAGREE"), 80, 40.0,
                List.of(), List.of("Write a general option argument."),
                "No reliable demographic concentration passes the narration rules.");
        return new UnwrappedResearchRequest(
                42L,
                "A city is considering a workplace parking levy.",
                "Should the city introduce a workplace parking levy?",
                "UNITED_KINGDOM",
                200,
                "aggregate-v1",
                List.of(agree, disagree));
    }

    private static ChatResponse responseWithCitation(String citation) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("""
                        {"output":[{"content":[{"annotations":[
                          {"type":"url_citation","url":"%s"}
                        ]}]}]}
                        """.formatted(citation))
                .build();
        OpenAiResponsesChatResponseMetadata metadata =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("provider-grok-4.5")
                        .rawHttpResponse(rawResponse)
                        .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .metadata(metadata)
                .build();
    }
}
