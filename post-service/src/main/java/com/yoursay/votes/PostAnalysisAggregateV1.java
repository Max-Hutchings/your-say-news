package com.yoursay.votes;

import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import java.time.Instant;
import java.util.List;

/**
 * Immutable, identity-free statistical input for Post Unwrapped. It deliberately contains no vote
 * row, user id, email, or individual characteristic snapshot.
 */
public record PostAnalysisAggregateV1(
        String schemaVersion,
        Long postId,
        VotingType votingType,
        String summary,
        String question,
        String jurisdiction,
        List<VoteOptionDto> options,
        long canonicalVoteCount,
        String aggregateVersion,
        Instant capturedAt,
        List<OverallOptionStatisticV1> overall,
        List<CohortAggregateV1> cohorts,
        AggregationMetadataV1 metadata
) {
}
