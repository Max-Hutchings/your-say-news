package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkStatus;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnwrappedBenchmarkRunnerTest {
    @Test
    void reusesOnePreparedRequestAndKeepsOrderedSuccessesWhenOnePromptFails() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        UnwrappedResearchDraftV1 draft = representativeDraft();
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), eq("Prompt A"))).thenReturn(
                new UnwrappedResearchResult(draft, List.of(), "model-a", "response-a"));
        when(generator.generate(same(request), eq("Prompt B"))).thenThrow(
                new IllegalArgumentException("UNWRAPPED_DRAFT_INVALID: missing source"));
        when(generator.generate(same(request), eq("Prompt C"))).thenReturn(
                new UnwrappedResearchResult(draft, List.of(), "model-c", "response-c"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var variants = runner.run(42L, List.of("Prompt A", "Prompt B", "Prompt C"));

        assertEquals(List.of(1, 2, 3), variants.stream().map(value -> value.position()).toList());
        assertEquals(List.of("Prompt A", "Prompt B", "Prompt C"),
                variants.stream().map(value -> value.systemPrompt()).toList());
        assertEquals(List.of(
                        UnwrappedBenchmarkStatus.SUCCEEDED,
                        UnwrappedBenchmarkStatus.FAILED,
                        UnwrappedBenchmarkStatus.SUCCEEDED),
                variants.stream().map(value -> value.status()).toList());
        assertEquals("model-a", variants.getFirst().model());
        assertEquals("response-a", variants.getFirst().providerResponseId());
        assertEquals(List.of(71L), variants.getFirst().argumentPages().stream()
                .map(page -> page.optionId()).toList());
        assertEquals("Commuters weigh the cost of cleaner streets",
                variants.getFirst().argumentPages().getFirst().headline());
        assertEquals(List.of("source-1"), variants.getFirst().argumentPages().getFirst()
                .sources().stream().map(source -> source.id()).toList());
        assertNull(variants.getFirst().errorCode());
        assertNull(variants.getFirst().errorMessage());
        assertEquals("UNWRAPPED_DRAFT_INVALID", variants.get(1).errorCode());
        assertEquals("This prompt did not produce a valid Unwrapped draft (UNWRAPPED_DRAFT_INVALID).",
                variants.get(1).errorMessage());
        assertEquals(List.of(), variants.get(1).argumentPages());
        assertNull(variants.get(1).model());
        assertNull(variants.get(1).providerResponseId());
        assertEquals("response-c", variants.getLast().providerResponseId());
        verify(preparation).prepare(42L);
        verify(generator).generate(request, "Prompt A");
        verify(generator).generate(request, "Prompt B");
        verify(generator).generate(request, "Prompt C");
    }

    @Test
    void mapsUnexpectedProviderDetailsToASafeGenericLaneFailure() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), eq("A"))).thenThrow(
                new IllegalStateException("sensitive provider detail"));
        when(generator.generate(same(request), eq("B"))).thenReturn(
                new UnwrappedResearchResult(representativeDraft(), List.of(), "model-b", "response-b"));
        when(generator.generate(same(request), eq("C"))).thenReturn(
                new UnwrappedResearchResult(representativeDraft(), List.of(), "model-c", "response-c"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var failure = runner.run(42L, List.of("A", "B", "C")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.FAILED, failure.status());
        assertEquals("UNWRAPPED_GENERATION_FAILED", failure.errorCode());
        assertEquals("This prompt did not produce a valid Unwrapped draft (UNWRAPPED_GENERATION_FAILED).",
                failure.errorMessage());
        verify(generator).generate(request, "A");
    }

    @Test
    void repairsSuccessiveModelFormatFailuresAndReturnsTheOriginalPrompt() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        UnwrappedResearchDraftV1 draft = representativeDraft();
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), anyString()))
                .thenThrow(new IllegalArgumentException("UNWRAPPED_HEADLINE_WORDS"))
                .thenThrow(new IllegalArgumentException("UNWRAPPED_ARTICLE_WORDS"))
                .thenReturn(new UnwrappedResearchResult(draft, List.of(), "grok-4.5", "response-3"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var result = runner.run(42L, List.of("Write a concise researched comparison.")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.SUCCEEDED, result.status());
        assertEquals("Write a concise researched comparison.", result.systemPrompt());
        assertEquals(3, result.attemptCount());
        assertEquals(repairPrompt("Write a concise researched comparison.",
                "UNWRAPPED_ARTICLE_WORDS"), result.effectiveSystemPrompt());
        assertEquals("grok-4.5", result.model());
        assertEquals("response-3", result.providerResponseId());
        assertEquals(1, result.argumentPages().size());
        var page = result.argumentPages().getFirst();
        assertEquals(71L, page.optionId());
        assertEquals("Commuters weigh the cost of cleaner streets", page.headline());
        assertEquals(List.of("ageRange=AGE_25_34"), page.selectedCohortIds());
        assertEquals(List.of(
                "Younger commuters may favour reliable public transport because predictable services reduce the financial and practical pressures of travelling to work each day.",
                "Official transport evidence provides wider context for that choice while the analysis remains limited to people who voted on this specific post."),
                page.paragraphs().stream().map(paragraph -> paragraph.text()).toList());
        assertEquals("This analysis describes patterns among people who voted on this post; "
                + "it cannot know every individual's reason.", page.caveat());
        assertEquals(List.of("source-1"), page.sources().stream().map(source -> source.id()).toList());
        assertEquals(List.of("https://www.ons.gov.uk/transport"),
                page.sources().stream().map(source -> source.url()).toList());

        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(generator, times(3)).generate(same(request), prompts.capture());
        assertEquals(List.of(
                        "Write a concise researched comparison.",
                        repairPrompt("Write a concise researched comparison.",
                                "UNWRAPPED_HEADLINE_WORDS"),
                        repairPrompt("Write a concise researched comparison.",
                                "UNWRAPPED_ARTICLE_WORDS")),
                prompts.getAllValues());
    }

    @Test
    void stopsAfterFiveModelFormatAttemptsAndReturnsTheLastValidationCode() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), anyString())).thenThrow(
                new IllegalArgumentException("UNWRAPPED_HEADLINE_WORDS"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var failure = runner.run(42L, List.of("Write a concise researched comparison.")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.FAILED, failure.status());
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure.errorCode());
        assertEquals("This prompt did not produce a valid Unwrapped draft (UNWRAPPED_HEADLINE_WORDS).",
                failure.errorMessage());
        assertEquals(List.of(), failure.argumentPages());
        assertEquals(5, failure.attemptCount());
        assertEquals(repairPrompt("Write a concise researched comparison.",
                "UNWRAPPED_HEADLINE_WORDS"), failure.effectiveSystemPrompt());
        verify(generator, times(5)).generate(same(request), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UNWRAPPED_DRAFT_MISSING",
            "UNWRAPPED_OPTION_PAGE_COUNT",
            "UNWRAPPED_OPTION_ORDER",
            "UNWRAPPED_HEADLINE_GENERIC",
            "UNWRAPPED_HEADLINE_WORDS",
            "UNWRAPPED_HEADLINE_COHORT",
            "UNWRAPPED_COHORTS_MISSING",
            "UNWRAPPED_TOO_MANY_COHORTS",
            "UNWRAPPED_INVENTED_COHORT",
            "UNWRAPPED_COHORT_REQUIRED",
            "UNWRAPPED_PARAGRAPH_COUNT",
            "UNWRAPPED_ARTICLE_WORDS",
            "UNWRAPPED_EXPLANATION_MISSING",
            "UNWRAPPED_PARAGRAPH_MISSING",
            "UNWRAPPED_PARAGRAPH_UNSOURCED",
            "UNWRAPPED_POPULATION_INFERENCE",
            "UNWRAPPED_SOURCES_MISSING",
            "UNWRAPPED_SOURCE_ID",
            "UNWRAPPED_DUPLICATE_SOURCE_ID",
            "UNWRAPPED_SOURCE_METADATA",
            "UNWRAPPED_SOURCE_URL_INVALID",
            "UNWRAPPED_SOURCE_URL_UNSAFE",
            "UNWRAPPED_OBSERVED_CAVEAT"
    })
    void retriesEveryModelCorrectableValidationCode(String validationCode) {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), anyString()))
                .thenThrow(new IllegalArgumentException(validationCode))
                .thenReturn(new UnwrappedResearchResult(
                        representativeDraft(), List.of(), "grok-4.5", "response-2"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var result = runner.run(42L, List.of("Write a concise researched comparison.")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.SUCCEEDED, result.status());
        assertEquals(2, result.attemptCount());
        assertEquals(repairPrompt("Write a concise researched comparison.", validationCode),
                result.effectiveSystemPrompt());
        verify(generator, times(2)).generate(same(request), anyString());
    }

    @Test
    void doesNotRetryNonFormatFailuresAndMapsTheirExactMessages() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), eq("Evidence prompt"))).thenThrow(
                new IllegalArgumentException("UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE"));
        when(generator.generate(same(request), eq("Provider prompt"))).thenThrow(
                new IllegalStateException("UNWRAPPED_PROVIDER_NOT_CONFIGURED"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var failures = runner.run(42L, List.of("Evidence prompt", "Provider prompt"));

        assertEquals(List.of(
                        "This post does not have reliable cohort evidence for every option.",
                        "The Unwrapped provider is not configured."),
                failures.stream().map(value -> value.errorMessage()).toList());
        assertEquals(List.of(1, 1), failures.stream().map(value -> value.attemptCount()).toList());
        assertEquals(List.of("Evidence prompt", "Provider prompt"),
                failures.stream().map(value -> value.effectiveSystemPrompt()).toList());
        verify(generator).generate(request, "Evidence prompt");
        verify(generator).generate(request, "Provider prompt");
    }

    private static UnwrappedResearchDraftV1 representativeDraft() {
        UnwrappedSourceDraftV1 source = new UnwrappedSourceDraftV1(
                "source-1", "https://www.ons.gov.uk/transport",
                "Office for National Statistics", "Transport data", SourceClassification.OFFICIAL);
        UnwrappedArgumentDraftV1 page = new UnwrappedArgumentDraftV1(
                71L,
                "Commuters weigh the cost of cleaner streets",
                List.of("ageRange=AGE_25_34"),
                List.of(
                        new UnwrappedArticleParagraphDraftV2(
                                "Younger commuters may favour reliable public transport because predictable services "
                                        + "reduce the financial and practical pressures of travelling to work each day.",
                                List.of("source-1")),
                        new UnwrappedArticleParagraphDraftV2(
                                "Official transport evidence provides wider context for that choice while the analysis "
                                        + "remains limited to people who voted on this specific post.",
                                List.of("source-1"))),
                "This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.");
        return new UnwrappedResearchDraftV1(List.of(page), List.of(source));
    }

    private static String repairPrompt(String systemPrompt, String validationCode) {
        return systemPrompt + """


                Your previous response failed validation (%s). Regenerate the complete response and
                check it before returning: include exactly one page for every supplied optionId in the supplied
                order; each headline must contain 6 to 10 whitespace-separated words; each page must contain
                2 or 3 non-empty paragraphs totalling 50 to 100 words; every paragraph must cite at least one
                real HTTPS source returned by web search in this call; every page must explain the choice using
                the word "because" or "reason"; and every caveat must exactly be:
                This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.
                """.formatted(validationCode);
    }
}
