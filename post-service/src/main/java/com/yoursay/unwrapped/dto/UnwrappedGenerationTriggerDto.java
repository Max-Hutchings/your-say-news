package com.yoursay.unwrapped.dto;

/** Acknowledges that normal Unwrapped milestone reconciliation was requested manually. */
public record UnwrappedGenerationTriggerDto(
        Long postId,
        String status
) {
}
