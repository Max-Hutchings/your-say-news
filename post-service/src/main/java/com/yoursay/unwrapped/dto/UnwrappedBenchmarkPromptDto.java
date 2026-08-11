package com.yoursay.unwrapped.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** Exact instruction and aggregate context shown to the administrator benchmark editors. */
public record UnwrappedBenchmarkPromptDto(
        String systemPrompt,
        String outputInstructions,
        JsonNode input
) {
}
