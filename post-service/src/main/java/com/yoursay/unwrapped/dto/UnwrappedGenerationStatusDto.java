package com.yoursay.unwrapped.dto;

import java.time.Instant;

/** Persistent progress for the latest Unwrapped work associated with one post. */
public record UnwrappedGenerationStatusDto(
        Long postId,
        UnwrappedGenerationState state,
        int queuedJobs,
        int generatingJobs,
        int readyJobs,
        int failedJobs,
        Instant updatedAt,
        String errorMessage
) {
}
