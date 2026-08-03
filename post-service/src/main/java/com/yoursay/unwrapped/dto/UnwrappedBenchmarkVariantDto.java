package com.yoursay.unwrapped.dto;

import java.util.List;

/** One ordered benchmark lane. Failed lanes intentionally carry no draft pages. */
public record UnwrappedBenchmarkVariantDto(
        int position,
        String systemPrompt,
        String effectiveSystemPrompt,
        int attemptCount,
        UnwrappedBenchmarkStatus status,
        String model,
        String providerResponseId,
        List<UnwrappedArgumentPageDto> argumentPages,
        String errorCode,
        String errorMessage
) {
}
