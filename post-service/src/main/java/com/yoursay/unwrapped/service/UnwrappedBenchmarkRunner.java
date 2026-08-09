package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkStatus;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkVariantDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Executes bounded prompt comparisons without creating publication lifecycle records. */
@ApplicationScoped
class UnwrappedBenchmarkRunner {
    private static final int MAX_FORMAT_ATTEMPTS = 5;
    private static final Set<String> RETRYABLE_FORMAT_CODES = Set.of(
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
            "UNWRAPPED_OBSERVED_CAVEAT");

    @Inject
    UnwrappedResearchPreparation preparation;
    @Inject
    UnwrappedResearchGenerator generator;

    List<UnwrappedBenchmarkVariantDto> run(Long postId, List<String> systemPrompts) {
        UnwrappedResearchRequest request = preparation.prepare(postId).request();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<UnwrappedBenchmarkVariantDto>> futures = new ArrayList<>(systemPrompts.size());
            for (int index = 0; index < systemPrompts.size(); index++) {
                int position = index + 1;
                String prompt = systemPrompts.get(index);
                futures.add(executor.submit(() -> generate(position, prompt, request)));
            }
            return futures.stream().map(UnwrappedBenchmarkRunner::await).toList();
        }
    }

    private UnwrappedBenchmarkVariantDto generate(
            int position,
            String systemPrompt,
            UnwrappedResearchRequest request
    ) {
        String attemptPrompt = systemPrompt;
        for (int attempt = 1; attempt <= MAX_FORMAT_ATTEMPTS; attempt++) {
            try {
                UnwrappedResearchResult result = generator.generate(request, attemptPrompt);
                return new UnwrappedBenchmarkVariantDto(
                        position, systemPrompt, attemptPrompt, attempt,
                        UnwrappedBenchmarkStatus.SUCCEEDED,
                        result.model(), result.providerResponseId(),
                        UnwrappedStoryResponseAssembler.argumentPages(result.draft()), null, null);
            } catch (RuntimeException failure) {
                String code = errorCode(failure);
                if (attempt < MAX_FORMAT_ATTEMPTS && RETRYABLE_FORMAT_CODES.contains(code)) {
                    Log.warnf("Unwrapped benchmark format retry: postId=%d lane=%d attempt=%d code=%s",
                            request.postId(), position, attempt, code);
                    attemptPrompt = repairPrompt(systemPrompt, code);
                    continue;
                }
                return new UnwrappedBenchmarkVariantDto(
                        position, systemPrompt, attemptPrompt, attempt,
                        UnwrappedBenchmarkStatus.FAILED,
                        null, null, List.of(), code, failureMessage(code));
            }
        }
        throw new IllegalStateException("UNWRAPPED_BENCHMARK_ATTEMPTS_EXHAUSTED");
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

    private static UnwrappedBenchmarkVariantDto await(Future<UnwrappedBenchmarkVariantDto> future) {
        try {
            return future.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("UNWRAPPED_BENCHMARK_INTERRUPTED", interrupted);
        } catch (ExecutionException failure) {
            throw new IllegalStateException("UNWRAPPED_BENCHMARK_FAILED", failure.getCause());
        }
    }

    private static String errorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || !message.startsWith("UNWRAPPED_")) {
            return "UNWRAPPED_GENERATION_FAILED";
        }
        int separator = message.indexOf(':');
        return separator < 0 ? message : message.substring(0, separator);
    }

    private static String failureMessage(String code) {
        return switch (code) {
            case "UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE" ->
                    "This post does not have reliable cohort evidence for every option.";
            case "UNWRAPPED_PROVIDER_NOT_CONFIGURED" ->
                    "The Unwrapped provider is not configured.";
            default -> "This prompt did not produce a valid Unwrapped draft (" + code + ").";
        };
    }
}
