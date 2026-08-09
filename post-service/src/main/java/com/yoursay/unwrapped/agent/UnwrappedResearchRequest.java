package com.yoursay.unwrapped.agent;

import com.yoursay.unwrapped.selection.OptionBriefV1;
import java.util.List;

public record UnwrappedResearchRequest(
        Long postId,
        String summary,
        String question,
        String jurisdiction,
        long canonicalVoteCount,
        String aggregateVersion,
        List<OptionBriefV1> options
) {
}
