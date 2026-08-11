package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchGenerator;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedResearchResult;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkStatus;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkVariantDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Executes bounded prompt comparisons without creating publication lifecycle records. */
@ApplicationScoped
class UnwrappedBenchmarkRunner {
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
        try {
            UnwrappedResearchResult result = generator.generate(request, systemPrompt);
            return new UnwrappedBenchmarkVariantDto(
                    position, systemPrompt, systemPrompt, 1,
                    UnwrappedBenchmarkStatus.SUCCEEDED,
                    result.model(), result.providerResponseId(),
                    UnwrappedStoryResponseAssembler.argumentPages(result.draft()), null, null);
        } catch (RuntimeException failure) {
            String code = errorCode(failure);
            return new UnwrappedBenchmarkVariantDto(
                    position, systemPrompt, systemPrompt, 1,
                    UnwrappedBenchmarkStatus.FAILED,
                    null, null, List.of(), code, failureMessage(code));
        }
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
