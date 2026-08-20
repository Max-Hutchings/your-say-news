package com.yoursay.autopost.dto;

import com.yoursay.autopost.AutoPostRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AutoPostRunDto(
        UUID id,
        AutoPostRunStatus status,
        Instant windowStart,
        Instant windowEnd,
        List<AutoPostCandidateDto> candidates,
        UUID selectedCandidateId,
        UUID pepperDraftId,
        AutoPostDraftDto draft,
        Long publishedPostId,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
