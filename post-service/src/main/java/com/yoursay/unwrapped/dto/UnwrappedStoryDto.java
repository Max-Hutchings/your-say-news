package com.yoursay.unwrapped.dto;

import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
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
        List<UnwrappedArgumentDraftV1> argumentPages,
        List<UnwrappedSourceDraftV1> sources,
        String reconsiderationQuestion,
        List<VoteOptionDto> reconsiderationOptions
) {
}
