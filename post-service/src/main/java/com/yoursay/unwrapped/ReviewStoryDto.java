package com.yoursay.unwrapped;

import com.yoursay.unwrapped.model.UnwrappedReviewStatus;
import java.time.Instant;
import java.util.UUID;

public record ReviewStoryDto(
        UUID storyId,
        Long postId,
        UnwrappedMode mode,
        Integer milestone,
        long canonicalVoteCount,
        UnwrappedReviewStatus status,
        Instant generatedAt,
        UnwrappedResearchDraftV1 draft
) {
}
