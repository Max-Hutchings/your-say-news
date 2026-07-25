package com.yoursay.unwrapped;

import com.yoursay.posts.VoteOptionDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UnwrappedStoryDto(
        String schemaVersion,
        UUID storyId,
        Long postId,
        UnwrappedMode mode,
        Integer milestone,
        long canonicalVoteCount,
        String aggregateVersion,
        Instant generatedAt,
        String model,
        List<UnwrappedArgumentDraftV1> argumentPages,
        List<UnwrappedSourceDraftV1> sources,
        String reconsiderationQuestion,
        List<VoteOptionDto> reconsiderationOptions
) {
}
