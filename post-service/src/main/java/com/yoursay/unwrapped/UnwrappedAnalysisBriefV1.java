package com.yoursay.unwrapped;

import java.util.List;

/** Aggregate-only brief supplied to the research generator for an observed story. */
public record UnwrappedAnalysisBriefV1(
        String schemaVersion,
        Long postId,
        String summary,
        String question,
        String jurisdiction,
        long canonicalVoteCount,
        String aggregateVersion,
        List<OptionBriefV1> options
) {
}
