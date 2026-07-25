package com.yoursay.unwrapped;

import java.util.List;

public record UnwrappedResearchRequest(
        UnwrappedMode mode,
        Long postId,
        String summary,
        String question,
        String jurisdiction,
        long canonicalVoteCount,
        String aggregateVersion,
        List<OptionBriefV1> options
) {
}
