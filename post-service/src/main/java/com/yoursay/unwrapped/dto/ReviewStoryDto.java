package com.yoursay.unwrapped.dto;

import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.model.UnwrappedReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewStoryDto(
        UUID storyId,
        Long postId,
        Integer milestone,
        long canonicalVoteCount,
        UnwrappedReviewStatus status,
        Instant generatedAt,
        String notice,
        List<VoteOptionDto> options,
        List<UnwrappedArgumentPageDto> argumentPages
) {
}
