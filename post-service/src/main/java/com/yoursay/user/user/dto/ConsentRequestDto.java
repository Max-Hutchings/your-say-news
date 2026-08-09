package com.yoursay.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Explicit agreement to the currently published privacy policy. */
public record ConsentRequestDto(
        @NotBlank
        @Pattern(regexp = "2026-06-01")
        String privacyPolicyVersion
) {
}
