package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.selection.CandidateRole;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.unwrapped.validation.SourceUrlPolicy;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import com.yoursay.votes.dto.CohortDimensionV1;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.control.ActivateRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        assertDoesNotThrow(() -> new UnwrappedDraftValidator(new SourceUrlPolicy())
                .validate(request, result.draft(), result.providerCitations()));
        verifyNoInteractions(aiService);
    }

    @Test
    void developmentStubLimitsCohortsAndBuildsTheNoCohortFallback() {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        OptionBriefV1 cohortOption = new OptionBriefV1(
                new VoteOptionDto(15L, "Reduce local tax", 0, "AGREE"), 377L, 62.83,
                List.of(
                        cohort("gender=MAN", "Men", "MAN"),
                        cohort("gender=WOMAN", "Women", "WOMAN"),
                        cohort("gender=NON_BINARY", "Non-binary people", "NON_BINARY")),
                List.of("Explain the option-specific pattern."), null);
        OptionBriefV1 generalOption = new OptionBriefV1(
                new VoteOptionDto(16L, "Keep local services", 1, "DISAGREE"), 223L, 37.17,
                List.of(), List.of("Write the strongest researched general case."),
                "No privacy-safe characteristic group is available.");
        UnwrappedResearchRequest request = new UnwrappedResearchRequest(
                2005L, "The council is reviewing local tax.",
                "Should local tax be reduced?", "UNITED_KINGDOM", 600L,
                "aggregate-v4", List.of(cohortOption, generalOption));

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.stubbed = true;

        UnwrappedResearchResult result = generator.generate(request);

        assertEquals(List.of(15L, 16L), result.draft().pages().stream()
                .map(UnwrappedArgumentDraftV1::optionId).toList());
        assertEquals(List.of("gender=MAN", "gender=WOMAN"),
                result.draft().pages().getFirst().selectedCohortIds());
        assertEquals(List.of(), result.draft().pages().getLast().selectedCohortIds());
        assertEquals("Why people choosing keep local services favour the development option",
                result.draft().pages().getLast().headline());
        assertEquals(2, result.draft().pages().getFirst().paragraphs().size());
        assertTrue(result.draft().pages().getFirst().paragraphs().getLast().text()
                .contains("377 votes, or 62.83 percent"));
        assertTrue(result.draft().pages().getLast().paragraphs().getLast().text()
                .contains("223 votes, or 37.17 percent"));
        assertEquals("This analysis describes patterns among people who voted on this post; "
                        + "it cannot know every individual's reason.",
                result.draft().pages().getLast().caveat());
        assertEquals(List.of("stub-source"), result.draft().pages().getLast()
                .paragraphs().getLast().sourceIds());
        assertDoesNotThrow(() -> new UnwrappedDraftValidator(new SourceUrlPolicy())
                .validate(request, result.draft(), result.providerCitations()));
        verifyNoInteractions(aiService);
    }

    @Test
    void appendsRequiredOutputToTheProductionSystemPromptAndSendsOnlyInputAsUserMessage()
            throws Exception {
        UnwrappedResearchRequest request = representativeRequest();
        UnwrappedResearchDraftV1 draft = validDraft(request, "https://www.ons.gov.uk/source");
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/source");
        ObjectMapper objectMapper = new ObjectMapper();
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        DomainMetrics metrics = mock(DomainMetrics.class);
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, objectMapper, metrics);
        generator.configuredModel = "configured-fallback-model";

        UnwrappedResearchResult result = generator.generate(request);

        assertSame(draft, result.draft());
        assertEquals(List.of("https://www.ons.gov.uk/source"), result.providerCitations());
        assertEquals("provider-grok-4.5", result.model());
        assertEquals("response-42", result.providerResponseId());
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outputInstructions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiService).research(
                systemPrompt.capture(), outputInstructions.capture(), input.capture());
        assertEquals(expectedSystemPrompt(), systemPrompt.getValue());
        assertEquals(expectedOutputInstructions(), outputInstructions.getValue());
        assertTrue(!outputInstructions.getValue().contains("{{pageCount}}"));
        assertTrue(!outputInstructions.getValue().contains("{{optionIds}}"));

        assertTrue(!input.getValue().contains("Required output contract"));
        assertTrue(!input.getValue().contains("You must call web search"));
        assertEquals("INPUT JSON:\n" + objectMapper.writeValueAsString(request) + "\n",
                input.getValue());
        assertTrue(input.getValue().contains(
                "\"summary\":\"A city is considering a workplace parking levy.\""));
        assertTrue(input.getValue().contains(
                "\"question\":\"Should the city introduce a workplace parking levy?\""));
        assertTrue(input.getValue().contains("\"canonicalVoteCount\":200"));
        assertTrue(input.getValue().contains("\"aggregateVersion\":\"aggregate-v1\""));
        assertTrue(input.getValue().contains(
                "\"overallVoteCount\":120,\"overallVotePercentage\":60.0"));
        assertTrue(input.getValue().contains(
                "\"overallVoteCount\":80,\"overallVotePercentage\":40.0"));
        assertTrue(input.getValue().contains(
                "\"sampleSize\":60,\"populationSharePercentage\":60.0,\"optionVoteCount\":45"));
        assertTrue(input.getValue().contains(
                "\"compositionPercentage\":75.0,\"propensityPercentage\":75.0,"));
        assertTrue(input.getValue().contains(
                "\"overIndexPercentagePoints\":15.0,\"differenceFromRestPercentagePoints\":30.0"));
        assertTrue(input.getValue().contains(
                "\"wilson95Low\":62.0,\"wilson95High\":84.0,\"adjustedQValue\":0.001"));
        assertTrue(input.getValue().contains("\"label\":\"GBP 25k to GBP 40k\""));
        assertTrue(input.getValue().contains("\"marketLabel\":\"United Kingdom\""));
        assertTrue(input.getValue().contains("\"lowerInclusive\":25000"));
        assertTrue(input.getValue().contains("\"upperExclusive\":40000"));
        assertTrue(input.getValue().contains("\"measureLabel\":\"Annual personal income before tax\""));
        assertTrue(!input.getValue().contains("exactIncome"));
        assertTrue(!input.getValue().contains("userId"));
        Set<String> requestFields = new HashSet<>();
        collectFieldNames(objectMapper.valueToTree(request), requestFields);
        assertTrue(UNWRAPPED_REQUEST_ALLOWED_FIELDS.containsAll(requestFields),
                () -> "Unexpected Unwrapped request fields: "
                        + difference(requestFields, UNWRAPPED_REQUEST_ALLOWED_FIELDS));
        verify(metrics).recordOperation(eq("unwrapped"), eq("research_provider"),
                eq("success"), eq("none"), eq("none"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void appendsRequiredOutputAfterTheEditableBenchmarkSystemPrompt() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchDraftV1 draft = validDraft(
                request, "https://www.ons.gov.uk/benchmark");
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));
        generator.configuredModel = "grok-test";

        generator.generate(request, "Use a deliberately terse editorial voice.");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outputInstructions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiService).research(
                systemPrompt.capture(), outputInstructions.capture(), input.capture());
        verifyNoMoreInteractions(aiService);
        assertEquals("Use a deliberately terse editorial voice.", systemPrompt.getValue());
        assertEquals(expectedOutputInstructions(), outputInstructions.getValue());
        assertTrue(!input.getValue().contains("Required output contract"));
        assertTrue(input.getValue().contains("\"candidates\":[]"));
    }

    @Test
    void benchmarkReturnsTheStructuredModelDraftWithoutValidationOrCitationMetadata() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchDraftV1 modelDraft = new UnwrappedResearchDraftV1(
                List.of(new UnwrappedArgumentDraftV1(
                        999L, "Short", null,
                        List.of(new UnwrappedArticleParagraphDraftV2(
                                "The model returned this unchanged.", null)),
                        "A model-written caveat.")),
                List.of());
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse responseWithoutCitations = response("{}", "provider-grok-4.5");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(
                    responseWithoutCitations, mock(ChatRequest.class), ModelProvider.OPEN_AI,
                    new HashMap<>()));
            return modelDraft;
        });
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));
        generator.validator = validator;

        UnwrappedResearchResult result = generator.generate(request, "Benchmark prompt");

        assertSame(modelDraft, result.draft());
        assertEquals(List.of(), result.providerCitations());
        assertEquals("provider-grok-4.5", result.model());
        assertEquals("response-42", result.providerResponseId());
        verifyNoInteractions(validator);
    }

    @Test
    void benchmarkDoesNotRequireCapturedProviderMetadata() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchDraftV1 modelDraft = validDraft(
                request, "https://www.ons.gov.uk/model-source");
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        when(aiService.research(anyString(), anyString(), anyString())).thenReturn(modelDraft);
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));
        generator.configuredModel = "configured-grok-model";

        UnwrappedResearchResult result = generator.generate(request, "Benchmark prompt");

        assertSame(modelDraft, result.draft());
        assertEquals(List.of(), result.providerCitations());
        assertEquals("configured-grok-model", result.model());
        assertNull(result.providerResponseId());
    }

    @Test
    void classifiesANullStructuredDraftAsRetryableDraftFailure() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return null;
        });

        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));
        generator.configuredModel = "configured-fallback-model";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request, "Write a concise researched comparison."));

        assertEquals("UNWRAPPED_DRAFT_MISSING", failure.getMessage());
        verify(aiService).research(anyString(), anyString(), anyString());
    }

    @Test
    void describesAnEmptyDraftUsingSafeProviderCompletionMetadata() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"pages\":[],\"sources\":[]}"))
                .id("response-sensitive-42")
                .modelName("grok-4.5")
                .tokenUsage(new TokenUsage(1_240, 18, 1_258))
                .finishReason(FinishReason.STOP)
                .build();

        String message = LangChain4jUnwrappedResearchGenerator.missingDraftLogMessage(
                response, new UnwrappedResearchDraftV1(List.of(), List.of()));

        assertEquals("Unwrapped provider returned no draft content: domain=unwrapped "
                + "operation=research_provider outcome=dependency_error "
                + "errorType=provider_contract errorCode=UNWRAPPED_DRAFT_MISSING "
                + "providerResponseCaptured=true model=grok-4.5 finishReason=STOP "
                + "inputTokens=1240 outputTokens=18 totalTokens=1258 "
                + "responseTextChars=25 toolRequests=0 pages=0 nonNullPages=0", message);
        assertFalse(message.contains("response-sensitive-42"));
        assertFalse(message.contains("sources"));

        ChatResponse hostileMetadata = ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .modelName("grok\napi-key=sensitive")
                .build();
        String hostileMessage = LangChain4jUnwrappedResearchGenerator.missingDraftLogMessage(
                hostileMetadata, null);
        assertTrue(hostileMessage.contains("model=unknown"));
        assertFalse(hostileMessage.contains("sensitive"));
    }

    @Test
    void rejectsBenchmarkEntitiesWithoutAnyArgumentPageContent() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        when(aiService.research(anyString(), anyString(), anyString())).thenReturn(
                new UnwrappedResearchDraftV1(null, List.of()),
                new UnwrappedResearchDraftV1(List.of(), List.of()),
                new UnwrappedResearchDraftV1(Collections.singletonList(null), List.of()),
                new UnwrappedResearchDraftV1(
                        Collections.nCopies(2, (UnwrappedArgumentDraftV1) null), List.of()));
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));

        for (int attempt = 0; attempt < 4; attempt++) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> generator.generate(request, "Benchmark prompt"));

            assertEquals("UNWRAPPED_DRAFT_MISSING", failure.getMessage());
        }
        verify(aiService, times(4)).research(anyString(), anyString(), anyString());
    }

    @Test
    void benchmarkAcceptsEntityWithAnyArgumentPageContent() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedArgumentDraftV1 modelPage = new UnwrappedArgumentDraftV1(
                15L, "Model headline", null,
                List.of(new UnwrappedArticleParagraphDraftV2("Model paragraph", null)),
                "Model caveat");
        List<UnwrappedArgumentDraftV1> leadingNullPages = new ArrayList<>();
        leadingNullPages.add(null);
        leadingNullPages.add(modelPage);
        UnwrappedResearchDraftV1 leadingNullDraft =
                new UnwrappedResearchDraftV1(leadingNullPages, List.of());
        List<UnwrappedArgumentDraftV1> trailingNullPages = new ArrayList<>();
        trailingNullPages.add(modelPage);
        trailingNullPages.add(null);
        UnwrappedResearchDraftV1 trailingNullDraft =
                new UnwrappedResearchDraftV1(trailingNullPages, List.of());
        List<UnwrappedArgumentDraftV1> surroundingNullPages = new ArrayList<>();
        surroundingNullPages.add(null);
        surroundingNullPages.add(modelPage);
        surroundingNullPages.add(null);
        UnwrappedResearchDraftV1 surroundingNullDraft =
                new UnwrappedResearchDraftV1(surroundingNullPages, List.of());
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        when(aiService.research(anyString(), anyString(), anyString()))
                .thenReturn(leadingNullDraft, trailingNullDraft, surroundingNullDraft);
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));

        UnwrappedResearchResult leadingNullResult =
                generator.generate(request, "Benchmark prompt");
        UnwrappedResearchResult trailingNullResult =
                generator.generate(request, "Benchmark prompt");
        UnwrappedResearchResult surroundingNullResult =
                generator.generate(request, "Benchmark prompt");

        assertSame(leadingNullDraft, leadingNullResult.draft());
        assertNull(leadingNullResult.draft().pages().getFirst());
        assertSame(modelPage, leadingNullResult.draft().pages().get(1));
        assertSame(trailingNullDraft, trailingNullResult.draft());
        assertSame(modelPage, trailingNullResult.draft().pages().getFirst());
        assertNull(trailingNullResult.draft().pages().get(1));
        assertSame(surroundingNullDraft, surroundingNullResult.draft());
        assertNull(surroundingNullResult.draft().pages().getFirst());
        assertSame(modelPage, surroundingNullResult.draft().pages().get(1));
        assertNull(surroundingNullResult.draft().pages().get(2));
    }

    @Test
    void rejectsMissingDraftContentBeforeItCanReachPublicationValidation() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        List<UnwrappedResearchDraftV1> missingDrafts = new ArrayList<>();
        missingDrafts.add(null);
        missingDrafts.add(new UnwrappedResearchDraftV1(null, List.of()));
        missingDrafts.add(new UnwrappedResearchDraftV1(List.of(), List.of()));
        missingDrafts.add(new UnwrappedResearchDraftV1(
                Collections.singletonList(null), List.of()));
        missingDrafts.add(new UnwrappedResearchDraftV1(
                Collections.nCopies(2, (UnwrappedArgumentDraftV1) null), List.of()));
        var missingDraftIterator = missingDrafts.iterator();
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return missingDraftIterator.next();
        });
        DomainMetrics metrics = mock(DomainMetrics.class);
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), metrics);
        generator.validator = validator;

        for (int attempt = 0; attempt < 5; attempt++) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> generator.generate(request));

            assertEquals("UNWRAPPED_DRAFT_MISSING", failure.getMessage());
        }
        verifyNoInteractions(validator);
        verify(metrics, times(5)).recordOperation(eq("unwrapped"), eq("research_provider"),
                eq("dependency_error"), eq("provider_contract"),
                eq("UNWRAPPED_DRAFT_MISSING"),
                org.mockito.ArgumentMatchers.anyLong());
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void rejectsDraftSourcesThatDoNotMatchTheProviderCitations() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/provider-source");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return validDraft(request, "https://www.ons.gov.uk/invented-source");
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request));

        assertEquals("UNWRAPPED_SOURCE_NOT_PROVIDER_CITATION", failure.getMessage());
    }

    @Test
    void rejectsIdentityBearingProviderProseBeforePersistence() {
        String sourceUrl = "https://www.ons.gov.uk/provider-source";
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation(sourceUrl);
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return withFirstParagraphPrefix(validDraft(request, sourceUrl),
                    "Jane Smith voted for this option and can be contacted at jane@example.com.");
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request));

        assertEquals("UNWRAPPED_PII_RISK", failure.getMessage());
    }

    @Test
    void combinesTopLevelAndNestedCitationsWhileFilteringAndDeduplicating() {
        String sourceUrl = "https://www.ons.gov.uk/source";
        String supportingUrl = "https://www.gov.uk/supporting-source";
        String body = """
                {
                  "citations":["%s","%s","http://unsafe.example/source"],
                  "output":[{"content":[{"annotations":[
                    {"type":"url_citation","url":"%s"},
                    {"type":"url_citation","url":"%s"},
                    {"type":"file_citation","url":"https://files.example/not-a-web-citation"}
                  ]}]}]
                }
                """.formatted(sourceUrl, sourceUrl, sourceUrl, supportingUrl);
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = response(body, "provider-grok-4.5");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return validDraft(request, sourceUrl);
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));

        UnwrappedResearchResult result = generator.generate(request);

        assertEquals(List.of(sourceUrl, supportingUrl), result.providerCitations());
        assertFalse(result.providerCitations().contains("http://unsafe.example/source"));
    }

    @Test
    void retainsAnUppercaseHttpsProviderCitationAcceptedByTheUrlPolicy() {
        String sourceUrl = "HTTPS://www.ons.gov.uk/source";
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation(sourceUrl);
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return validDraft(request, sourceUrl);
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));

        assertEquals(List.of(sourceUrl), generator.generate(request).providerCitations());
    }

    @Test
    void rejectsMissingAndMalformedProviderCitationMetadata() {
        UnwrappedResearchRequest request = requestWithoutCohorts();

        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING",
                providerFailure(request, response("{}", "provider-grok-4.5")));
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_INVALID",
                providerFailure(request, response("{not-json", "provider-grok-4.5")));

        ChatResponse missingMetadata = ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .build();
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING",
                providerFailure(request, missingMetadata));

        OpenAiResponsesChatResponseMetadata missingRawResponse =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-without-raw-http")
                        .modelName("provider-grok-4.5")
                        .build();
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING",
                providerFailure(request, ChatResponse.builder()
                        .aiMessage(AiMessage.from("{}"))
                        .metadata(missingRawResponse)
                        .build()));

        OpenAiResponsesChatResponseMetadata nullBodyMetadata =
                mock(OpenAiResponsesChatResponseMetadata.class);
        SuccessfulHttpResponse nullBodyResponse = mock(SuccessfulHttpResponse.class);
        when(nullBodyMetadata.rawHttpResponse()).thenReturn(nullBodyResponse);
        when(nullBodyResponse.body()).thenReturn(null);
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING",
                providerFailure(request, ChatResponse.builder()
                        .aiMessage(AiMessage.from("{}"))
                        .metadata(nullBodyMetadata)
                        .build()));
    }

    @Test
    void rejectsEveryUnconfiguredApiKeyFormWithoutCallingTheProvider() {
        for (String apiKey : new String[]{null, "", "   ", "__not_configured__"}) {
            UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
            DomainMetrics metrics = mock(DomainMetrics.class);
            LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                    aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                    metrics);
            generator.apiKey = apiKey;

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> generator.generate(requestWithoutCohorts()));

            assertEquals("UNWRAPPED_PROVIDER_NOT_CONFIGURED", failure.getMessage());
            verifyNoInteractions(aiService);
            verify(metrics).recordOperation(eq("unwrapped"), eq("research_provider"),
                    eq("service_error"), eq("configuration"),
                    eq("UNWRAPPED_PROVIDER_NOT_CONFIGURED"),
                    org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Test
    void wrapsEveryProviderExceptionTypeAndAlwaysClearsCapturedResponses() {
        for (Exception providerFailure : List.of(
                new java.io.IOException("provider transport exposed detail"),
                new IllegalArgumentException("provider rejected its request"),
                new IllegalStateException("provider runtime exposed detail"))) {
            UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
            UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
            ChatResponse staleResponse = responseWithCitation("https://www.ons.gov.uk/stale");
            when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
                capture.onResponse(new ChatModelResponseContext(
                        staleResponse, mock(ChatRequest.class), ModelProvider.OPEN_AI,
                        new HashMap<>()));
                throw providerFailure;
            });
            DomainMetrics metrics = mock(DomainMetrics.class);
            LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                    aiService, capture, new ObjectMapper(), metrics);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> generator.generate(requestWithoutCohorts()));

            assertEquals("UNWRAPPED_PROVIDER_FAILURE", failure.getMessage());
            assertEquals(providerFailure.getClass(), failure.getCause().getClass());
            assertNull(capture.take());
            String logMessage = LangChain4jUnwrappedResearchGenerator.failureLogMessage(
                    "dependency_error", "provider", "UNWRAPPED_PROVIDER_FAILURE");
            assertEquals("Unwrapped research failed: domain=unwrapped "
                    + "operation=research_provider outcome=dependency_error "
                    + "errorType=provider errorCode=UNWRAPPED_PROVIDER_FAILURE", logMessage);
            assertFalse(logMessage.contains(providerFailure.getMessage()));
            verify(metrics).recordOperation(eq("unwrapped"), eq("research_provider"),
                    eq("dependency_error"), eq("provider"), eq("UNWRAPPED_PROVIDER_FAILURE"),
                    org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Test
    void classifiesKnownProviderFailuresWithoutExposingTheirMessages() {
        List<ProviderFailureCase> failures = List.of(
                new ProviderFailureCase(new TimeoutException("sensitive timeout"),
                        "UNWRAPPED_PROVIDER_TIMEOUT"),
                new ProviderFailureCase(new RateLimitException("sensitive quota"),
                        "UNWRAPPED_PROVIDER_RATE_LIMITED"),
                new ProviderFailureCase(new AuthenticationException("sensitive auth"),
                        "UNWRAPPED_PROVIDER_AUTHENTICATION"),
                new ProviderFailureCase(new ContentFilteredException("sensitive filter"),
                        "UNWRAPPED_PROVIDER_CONTENT_FILTERED"),
                new ProviderFailureCase(new InvalidRequestException("sensitive request"),
                        "UNWRAPPED_PROVIDER_REQUEST_INVALID"),
                new ProviderFailureCase(new InternalServerException("sensitive outage"),
                        "UNWRAPPED_PROVIDER_UNAVAILABLE"));

        for (ProviderFailureCase providerFailure : failures) {
            UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
            when(aiService.research(anyString(), anyString(), anyString()))
                    .thenThrow(providerFailure.failure());
            DomainMetrics metrics = mock(DomainMetrics.class);
            LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                    aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(), metrics);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> generator.generate(requestWithoutCohorts(), "Benchmark prompt"));

            assertEquals(providerFailure.errorCode(), failure.getMessage());
            assertSame(providerFailure.failure(), failure.getCause());
            assertFalse(failure.getMessage().contains(providerFailure.failure().getMessage()));
            verify(metrics).recordOperation(eq("unwrapped"), eq("research_provider"),
                    eq("dependency_error"), eq("provider"),
                    eq(providerFailure.errorCode()), org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Test
    void classifiesKnownProviderFailureThroughWrapperCauses() {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        TimeoutException timeout = new TimeoutException("sensitive nested timeout");
        when(aiService.research(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("outer wrapper", timeout));
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> generator.generate(requestWithoutCohorts(), "Benchmark prompt"));

        assertEquals("UNWRAPPED_PROVIDER_TIMEOUT", failure.getMessage());
        assertFalse(failure.getMessage().contains(timeout.getMessage()));
    }

    @Test
    void emitsOnlyTheStableProviderFailureCodeToTheActualLog() {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        when(aiService.research(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("api-key=sensitive-provider-detail"));
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));

        LogRecord log = captureLog(() -> assertThrows(IllegalStateException.class,
                () -> generator.generate(requestWithoutCohorts(), "Benchmark prompt")));

        assertEquals("Unwrapped research failed: domain=unwrapped "
                + "operation=research_provider outcome=dependency_error "
                + "errorType=provider errorCode=UNWRAPPED_PROVIDER_FAILURE", log.getMessage());
        assertFalse(log.getMessage().contains("sensitive-provider-detail"));
    }

    @Test
    void rejectsAProviderResultWhenTheRawResponseWasNotCaptured() {
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        when(aiService.research(anyString(), anyString(), anyString()))
                .thenReturn(validDraft(request, "https://www.ons.gov.uk/source"));
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, new UnwrappedChatResponseCapture(), new ObjectMapper(),
                mock(DomainMetrics.class));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> generator.generate(request));

        assertEquals("UNWRAPPED_PROVIDER_RESPONSE_MISSING", failure.getMessage());
    }

    @Test
    void usesTheConfiguredModelWhenProviderMetadataHasABlankModelName() {
        String sourceUrl = "https://www.ons.gov.uk/source";
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation(sourceUrl, "   ");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return validDraft(request, sourceUrl);
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));
        generator.configuredModel = "configured-grok-model";

        assertEquals("configured-grok-model", generator.generate(request).model());
    }

    @Test
    void bindsTheCompletedPromptAsTheDynamicSystemMessage() throws Exception {
        var method = UnwrappedResearchAiService.class
                .getDeclaredMethod("research", String.class, String.class, String.class);
        SystemMessage systemMessage = method.getAnnotation(SystemMessage.class);
        V systemPromptVariable = method.getParameters()[0].getAnnotation(V.class);
        V outputInstructionsVariable = method.getParameters()[1].getAnnotation(V.class);
        UserMessage userMessage = method.getParameters()[2].getAnnotation(UserMessage.class);

        assertArrayEquals(new String[]{"{{systemPrompt}}\n\n{{outputInstructions}}\n"},
                systemMessage.value());
        assertEquals("systemPrompt", systemPromptVariable.value());
        assertEquals("outputInstructions", outputInstructionsVariable.value());
        assertNotNull(userMessage);
    }

    private static SelectedCohortV1 cohort() {
        return cohort("gender=MAN", "Men", "MAN");
    }

    private static SelectedCohortV1 cohort(String cohortId, String displayName, String bucket) {
        return new SelectedCohortV1(cohortId,
                List.of(new CohortDimensionV1("gender", bucket)),
                CandidateRole.CORE_ANCHOR, "Broad core group", 60, 60,
                45, 75, 75, 15, 30, 62, 84, 0.001, displayName);
    }

    private static SelectedCohortV1 incomeCohort() {
        IncomeRangeDisplayDto income = new IncomeRangeDisplayDto(
                "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
                "GBP 25k to GBP 40k", "Annual personal income before tax in the United Kingdom",
                "25th to 50th percentile locally", "GB", "United Kingdom", "GBP",
                "PERSONAL", "Annual personal income before tax", 25_000L, 40_000L,
                "TIER_3", "GB-GBP-GROSS-2025-v1", 1, "PERSONAL_TIER_3");
        CohortDimensionV1 dimension = new CohortDimensionV1(
                "personalIncomeRange", income.bucketId(), income.label(), income);
        return new SelectedCohortV1(
                "personalIncomeRange=" + income.bucketId(), List.of(dimension),
                CandidateRole.CORE_ANCHOR, "Broad core group", 60, 60,
                45, 75, 75, 15, 30, 62, 84, 0.001,
                "People with annual personal income of GBP 25k to GBP 40k in the United Kingdom");
    }

    private static UnwrappedResearchRequest representativeRequest() {
        OptionBriefV1 agree = new OptionBriefV1(
                new VoteOptionDto(71L, "Agree", 0, "AGREE"), 120, 60.0,
                List.of(incomeCohort()), List.of("Explain the observed pattern."), null);
        OptionBriefV1 disagree = new OptionBriefV1(
                new VoteOptionDto(72L, "Disagree", 1, "DISAGREE"), 80, 40.0,
                List.of(incomeCohort()), List.of("Explain the observed pattern."), null);
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
        return responseWithCitation(citation, "provider-grok-4.5");
    }

    private static ChatResponse responseWithCitation(String citation, String modelName) {
        return response("""
                {"output":[{"content":[{"annotations":[
                  {"type":"url_citation","url":"%s"}
                ]}]}]}
                """.formatted(citation), modelName);
    }

    private static ChatResponse response(String body, String modelName) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body)
                .build();
        OpenAiResponsesChatResponseMetadata metadata =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName(modelName)
                        .rawHttpResponse(rawResponse)
                        .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .metadata(metadata)
                .build();
    }

    private static LangChain4jUnwrappedResearchGenerator liveGenerator(
            UnwrappedResearchAiService aiService,
            UnwrappedChatResponseCapture capture,
            ObjectMapper objectMapper,
            DomainMetrics metrics
    ) {
        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = objectMapper;
        generator.validator = new UnwrappedDraftValidator(new SourceUrlPolicy());
        generator.metrics = metrics;
        generator.apiKey = "test-key";
        generator.configuredModel = "configured-fallback-model";
        return generator;
    }

    private static UnwrappedResearchDraftV1 validDraft(
            UnwrappedResearchRequest request,
            String sourceUrl
    ) {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "source-1", sourceUrl, "Office for National Statistics",
                "Household costs and public finances", SourceClassification.OFFICIAL);
        List<UnwrappedArgumentDraftV1> pages = request.options().stream()
                .map(option -> validPage(option, source.id()))
                .toList();
        return new UnwrappedResearchDraftV1(pages, List.of(source));
    }

    private static UnwrappedArgumentDraftV1 validPage(OptionBriefV1 option, String sourceId) {
        boolean hasCohort = !option.candidates().isEmpty();
        String headline = hasCohort
                ? "Why GBP 25k to GBP 40k voters favour change"
                : "Why voters favour this practical policy option";
        List<String> selected = hasCohort
                ? List.of(option.candidates().getFirst().cohortId())
                : List.of();
        return new UnwrappedArgumentDraftV1(
                option.option().id(), headline, selected,
                List.of(
                        new UnwrappedArticleParagraphDraftV2(
                                "These voters are likely to favour this option because its immediate effect "
                                        + "addresses household pressures described by the supplied aggregate. "
                                        + "The explanation remains tied to the observed voting pattern rather "
                                        + "than claiming private knowledge about individual motivations.",
                                List.of(sourceId)),
                        new UnwrappedArticleParagraphDraftV2(
                                "Published national statistics provide relevant context about household costs "
                                        + "and public finances. That evidence may explain why the practical effect "
                                        + "of this option matters to the selected voters while preserving the "
                                        + "difference between researched context and observed votes.",
                                List.of(sourceId))),
                "This analysis describes patterns among people who voted on this post; "
                        + "it cannot know every individual's reason.");
    }

    private static UnwrappedResearchDraftV1 withFirstParagraphPrefix(
            UnwrappedResearchDraftV1 draft,
            String prefix
    ) {
        UnwrappedArgumentDraftV1 firstPage = draft.pages().getFirst();
        UnwrappedArticleParagraphDraftV2 firstParagraph = firstPage.paragraphs().getFirst();
        UnwrappedArgumentDraftV1 changedPage = new UnwrappedArgumentDraftV1(
                firstPage.optionId(), firstPage.headline(), firstPage.selectedCohortIds(),
                List.of(new UnwrappedArticleParagraphDraftV2(
                                prefix + " " + firstParagraph.text(), firstParagraph.sourceIds()),
                        firstPage.paragraphs().getLast()),
                firstPage.caveat());
        return new UnwrappedResearchDraftV1(
                List.of(changedPage, draft.pages().getLast()), draft.sources());
    }

    private static String providerFailure(
            UnwrappedResearchRequest request,
            ChatResponse response
    ) {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return validDraft(request, "https://www.ons.gov.uk/source");
        });
        LangChain4jUnwrappedResearchGenerator generator = liveGenerator(
                aiService, capture, new ObjectMapper(), mock(DomainMetrics.class));
        return assertThrows(IllegalStateException.class, () -> generator.generate(request))
                .getMessage();
    }

    private static String expectedOutputInstructions() {
        return """
                # Required output contract

                - Return exactly 2 pages.
                - Return pages in this exact `optionId` order: [71, 72].
                - Include every `optionId` exactly once; do not merge or omit options.
                - When an option supplies cohort candidates, select one or two of their IDs and name a
                  selected cohort in the headline using its supplied `displayName` or governed dimension label.
                - When an option supplies no cohort candidates, return an empty `selectedCohortIds` list,
                  write the strongest general researched case for that option, and do not invent a cohort.
                - Headlines must be catchy, 6 to 18 words, and must not use agreement or disagreement.
                - Write two or three paragraphs totalling 50 to 100 words for every page.
                - In those paragraphs, explain why the selected cohort, or voters choosing the option
                  when no cohort is supplied, are likely to favour that option.
                - Use the same group-led paragraph format for every voting option.
                - Include each selected characteristic group exactly once.
                - Start each selected characteristic group's explanation with its exact supplied `displayName` in bold on its own line.
                - Explain why that specific group differs from another relevant group.
                - Do not repeat the page headline before each paragraph.
                - When selected characteristic groups are available, do not add a generic argument paragraph that lacks a group.
                - Direct explanations using words such as because, led or drove are allowed.
                - Do not claim direct knowledge of every individual voter's private motivation.
                - Do not identify individual voters using personal names, email addresses, exact dates of birth or identity-linked vote claims.
                - You must call web search before drafting any page.
                - Give every paragraph one or more `sourceIds`; empty `sourceIds` are forbidden.
                - Include every referenced source exactly once in `sources`; empty `sources` are forbidden.
                - Include no more than 20 sources in total.
                - Copy each source URL exactly from an HTTPS URL returned by web search in this same call.
                - Do not include a source unless it directly supports context used in a paragraph.
                - Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.

                Each selected-group paragraph's `text` must begin in this Markdown shape:

                **People aged 18 to 24**

                Compared with older adults, people aged 18 to 24 may be more concerned because...
                """.strip();
    }

    /**
     * Reads the shipped prompt rather than duplicating it, so an editorial edit does not break this
     * test. What is still pinned is the wiring: the file we ship is what reaches the model, with the
     * output contract kept out of it.
     */
    private static String expectedSystemPrompt() throws Exception {
        try (var stream = LangChain4jUnwrappedResearchGeneratorTest.class.getClassLoader()
                .getResourceAsStream("prompts/unwrapped/system-prompt.md")) {
            assertNotNull(stream, "prompts/unwrapped/system-prompt.md must be on the classpath");
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        }
    }

    private static final Set<String> UNWRAPPED_REQUEST_ALLOWED_FIELDS = Set.of(
            "postId", "summary", "question", "jurisdiction", "canonicalVoteCount",
            "aggregateVersion", "options", "option", "id", "label", "ordinal",
            "semanticKey", "overallVoteCount", "overallVotePercentage", "candidates",
            "narrativeInstructions", "insufficientEvidence", "cohortId", "dimensions", "role",
            "relevanceReason", "sampleSize", "populationSharePercentage", "optionVoteCount",
            "compositionPercentage", "propensityPercentage", "overIndexPercentagePoints",
            "differenceFromRestPercentagePoints", "wilson95Low", "wilson95High",
            "adjustedQValue", "displayName", "axis", "bucket", "income", "bucketId",
            "contextLabel", "relativeLabel", "marketCode", "marketLabel", "currencyCode",
            "measure", "measureLabel", "lowerInclusive", "upperExclusive", "relativeTier",
            "profileId", "profileVersion", "bandId");

    private static void collectFieldNames(JsonNode node, Set<String> target) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                target.add(entry.getKey());
                collectFieldNames(entry.getValue(), target);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, target));
        }
    }

    private static Set<String> difference(Set<String> actual, Set<String> allowed) {
        Set<String> unexpected = new HashSet<>(actual);
        unexpected.removeAll(allowed);
        return unexpected;
    }

    private static LogRecord captureLog(Runnable action) {
        CapturingLogHandler logs = new CapturingLogHandler();
        Logger rootLogger = Logger.getLogger("");
        rootLogger.addHandler(logs);
        try {
            action.run();
        } finally {
            rootLogger.removeHandler(logs);
        }
        assertNotNull(logs.failure);
        return logs.failure;
    }

    private static final class CapturingLogHandler extends Handler {
        private LogRecord failure;

        @Override
        public void publish(LogRecord record) {
            if (record.getMessage().startsWith("Unwrapped research failed:")) {
                failure = record;
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private record ProviderFailureCase(RuntimeException failure, String errorCode) {
    }
}
