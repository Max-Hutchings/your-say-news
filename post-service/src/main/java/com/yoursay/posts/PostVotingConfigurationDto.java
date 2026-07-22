package com.yoursay.posts;

import java.util.List;

/** PII-free voting configuration shared with the votes domain. */
public record PostVotingConfigurationDto(Long postId, VotingType votingType, List<VoteOptionDto> options) {
    public boolean containsOption(Long optionId) {
        return optionId != null && options.stream().anyMatch(option -> option.id().equals(optionId));
    }
}
