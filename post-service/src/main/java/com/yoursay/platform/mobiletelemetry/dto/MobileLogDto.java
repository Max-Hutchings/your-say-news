package com.yoursay.platform.mobiletelemetry.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * One app log record, carried by a {@code log} event. {@code name} is a fixed diagnostic name such as
 * {@code auth.sign_in_failed}, or {@code console} for a captured {@code console.warn} /
 * {@code console.error}. {@code attributes} hold bounded codes only (for example the sign-in stage and
 * the provider's error code); {@code message} is console text, scrubbed again on the server.
 */
public record MobileLogDto(
        @Size(max = 8) String level,
        @Size(max = 64) String name,
        @Size(max = 2000) String message,
        @Size(max = 8) Map<@Size(max = 32) String, @Size(max = 128) String> attributes
) {
}
