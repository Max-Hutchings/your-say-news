package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.selection.CandidateRole;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.votes.dto.CohortDimensionV1;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.enterprise.context.control.ActivateRequestContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    void appendsRequiredOutputToTheProductionSystemPromptAndSendsOnlyInputAsUserMessage()
            throws Exception {
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(null, null);
        UnwrappedResearchRequest request = representativeRequest();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/source");
        ObjectMapper objectMapper = new ObjectMapper();
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = objectMapper;
        generator.apiKey = "test-key";
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
        assertEquals(UnwrappedSystemPrompt.DEFAULT, systemPrompt.getValue());
        assertTrue(systemPrompt.getValue().startsWith("# Post Unwrapped editorial brief\n\n"));
        assertTrue(!systemPrompt.getValue().contains("Do not use these causal words anywhere"));
        assertEquals(UnwrappedSystemPrompt.outputInstructions(List.of(71L, 72L)),
                outputInstructions.getValue());
        assertTrue(!outputInstructions.getValue().contains("{{pageCount}}"));
        assertTrue(!outputInstructions.getValue().contains("{{optionIds}}"));

        assertTrue(!input.getValue().contains("Required output contract"));
        assertTrue(!input.getValue().contains("You must call web search"));
        assertEquals("INPUT JSON:\n" + objectMapper.writeValueAsString(request) + "\n",
                input.getValue());
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
    }

    @Test
    void appendsRequiredOutputAfterTheEditableBenchmarkSystemPrompt() {
        UnwrappedResearchDraftV1 draft = mock(UnwrappedResearchDraftV1.class);
        UnwrappedResearchRequest request = requestWithoutCohorts();
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/benchmark");
        when(aiService.research(anyString(), anyString(), anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.apiKey = "test-key";
        generator.configuredModel = "grok-test";

        generator.generate(request, "Use a deliberately terse editorial voice.");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> outputInstructions = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(aiService).research(
                systemPrompt.capture(), outputInstructions.capture(), input.capture());
        verifyNoMoreInteractions(aiService);
        assertEquals("Use a deliberately terse editorial voice.", systemPrompt.getValue());
        assertEquals(UnwrappedSystemPrompt.outputInstructions(List.of(71L, 72L)),
                outputInstructions.getValue());
        assertTrue(!input.getValue().contains("Required output contract"));
        assertTrue(input.getValue().contains("\"candidates\":[]"));
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

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.apiKey = "test-key";
        generator.configuredModel = "configured-fallback-model";

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> generator.generate(request, "Write a concise researched comparison."));

        assertEquals("UNWRAPPED_DRAFT_MISSING", failure.getMessage());
        verify(aiService).research(anyString(), anyString(), anyString());
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
        return new SelectedCohortV1("gender=MAN",
                List.of(new CohortDimensionV1("gender", "MAN")),
                CandidateRole.CORE_ANCHOR, "Broad core group", 60, 60,
                45, 75, 75, 15, 30, 62, 84, 0.001, "Men");
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
}
