package com.yoursay.unwrapped;

import java.time.Instant;
import java.util.UUID;

public record FollowUpResponseDto(
        UUID id,
        Long postId,
        UUID storyId,
        Long originalOptionId,
        Long optionId,
        boolean changed,
        Instant createdAt
) {
}
