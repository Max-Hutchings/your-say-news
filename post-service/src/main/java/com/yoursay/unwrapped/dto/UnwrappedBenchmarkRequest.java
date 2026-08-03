package com.yoursay.unwrapped.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** One to three complete system-message replacements for one bounded comparison run. */
public record UnwrappedBenchmarkRequest(
        @NotNull
        @Size(min = 1, max = 3)
        List<@NotBlank @Size(max = 20_000) String> systemPrompts
) {
}
