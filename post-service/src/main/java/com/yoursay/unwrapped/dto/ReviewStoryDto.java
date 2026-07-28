package com.yoursay.unwrapped.dto;

import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.model.UnwrappedReviewStatus;
import java.time.Instant;
import java.util.UUID;

public record ReviewStoryDto(
        UUID storyId,
        Long postId,
        Integer milestone,
        long canonicalVoteCount,
        UnwrappedReviewStatus status,
        Instant generatedAt,
        UnwrappedResearchDraftV1 draft
) {
}
