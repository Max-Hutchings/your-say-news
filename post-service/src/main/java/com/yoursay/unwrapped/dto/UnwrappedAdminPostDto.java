package com.yoursay.unwrapped.dto;

import com.yoursay.posts.VotingType;

import java.time.Instant;
import java.util.List;

/** Post context and aggregate-only vote data for administrator-triggered Unwrapped analysis. */
public record UnwrappedAdminPostDto(
        Long postId,
        String summary,
        String question,
        String caseFor,
        String caseAgainst,
        String jurisdiction,
        VotingType votingType,
        Instant createdAt,
        long canonicalVoteCount,
        List<UnwrappedAdminVoteOptionDto> overall
) {
}
