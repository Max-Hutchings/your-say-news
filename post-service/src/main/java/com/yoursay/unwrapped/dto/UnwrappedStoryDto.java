package com.yoursay.unwrapped.dto;

import com.yoursay.posts.dto.VoteOptionDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UnwrappedStoryDto(
        String schemaVersion,
        UUID storyId,
        Long postId,
        Integer milestone,
        long canonicalVoteCount,
        String aggregateVersion,
        Instant generatedAt,
        String model,
        List<UnwrappedArgumentPageDto> argumentPages,
        String reconsiderationQuestion,
        List<VoteOptionDto> reconsiderationOptions
) {
}
