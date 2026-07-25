package com.yoursay.posts;

import java.util.List;

/** PII-free, immutable post context shared with the votes and Unwrapped domains. */
public record PostVotingConfigurationDto(
        Long postId,
        String summary,
        String question,
        String jurisdiction,
        VotingType votingType,
        List<VoteOptionDto> options
) {
    /** Compatibility constructor for callers that need only option validation. */
    public PostVotingConfigurationDto(Long postId, VotingType votingType, List<VoteOptionDto> options) {
        this(postId, null, null, "GLOBAL", votingType, options);
    }

    public boolean containsOption(Long optionId) {
        return optionId != null && options.stream().anyMatch(option -> option.id().equals(optionId));
    }
}
