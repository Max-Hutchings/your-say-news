package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkStatus;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkVariantDto;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
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
    void returnsTheFirstModelFormatFailureWithoutRetrying() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(same(request), eq("Write a concise researched comparison.")))
                .thenThrow(new IllegalArgumentException("UNWRAPPED_HEADLINE_WORDS"))
                .thenReturn(new UnwrappedResearchResult(
                        representativeDraft(), List.of(), "grok-4.5", "response-2"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        var failure = runner.run(42L, List.of("Write a concise researched comparison.")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.FAILED, failure.status());
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure.errorCode());
        assertEquals("This prompt did not produce a valid Unwrapped draft (UNWRAPPED_HEADLINE_WORDS).",
                failure.errorMessage());
        assertEquals(List.of(), failure.argumentPages());
        assertEquals(1, failure.attemptCount());
        assertEquals("Write a concise researched comparison.", failure.effectiveSystemPrompt());
        verify(generator).generate(request, "Write a concise researched comparison.");
    }

    @Test
    void displaysModelFieldsEvenWhenTheyBreakThePublicationContract() {
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        UnwrappedResearchGenerator generator = mock(UnwrappedResearchGenerator.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        UnwrappedResearchDraftV1 uncheckedDraft = new UnwrappedResearchDraftV1(
                List.of(new UnwrappedArgumentDraftV1(
                        999L, "Short", null,
                        List.of(
                                new UnwrappedArticleParagraphDraftV2(
                                        "Exactly as returned.", List.of("missing-source")),
                                new UnwrappedArticleParagraphDraftV2(
                                        "No source ids were returned.", null)),
                        "Different caveat")),
                null);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, request));
        when(generator.generate(request, "Benchmark prompt")).thenReturn(
                new UnwrappedResearchResult(
                        uncheckedDraft, List.of(), "model-a", "response-a"));
        UnwrappedBenchmarkRunner runner = new UnwrappedBenchmarkRunner();
        runner.preparation = preparation;
        runner.generator = generator;

        UnwrappedBenchmarkVariantDto variant = runner.run(
                42L, List.of("Benchmark prompt")).getFirst();

        assertEquals(UnwrappedBenchmarkStatus.SUCCEEDED, variant.status());
        assertEquals(999L, variant.argumentPages().getFirst().optionId());
        assertEquals("Short", variant.argumentPages().getFirst().headline());
        assertEquals(List.of(), variant.argumentPages().getFirst().selectedCohortIds());
        assertEquals(List.of("Exactly as returned.", "No source ids were returned."),
                variant.argumentPages().getFirst().paragraphs().stream()
                        .map(UnwrappedArticleParagraphDraftV2::text).toList());
        assertEquals(List.of("missing-source"), variant.argumentPages().getFirst().paragraphs()
                .getFirst().sourceIds());
        assertEquals(List.of(), variant.argumentPages().getFirst().paragraphs()
                .getLast().sourceIds());
        assertEquals(List.of(), variant.argumentPages().getFirst().sources());
        assertEquals("Different caveat", variant.argumentPages().getFirst().caveat());
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

}
