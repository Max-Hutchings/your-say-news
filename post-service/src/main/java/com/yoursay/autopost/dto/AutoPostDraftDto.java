package com.yoursay.autopost.dto;

import com.yoursay.posts.VotingType;

import java.util.List;
import java.util.UUID;

public record AutoPostDraftDto(
        UUID id,
        String summary,
        String supportQuestion,
        String caseFor,
        String caseAgainst,
        VotingType votingType,
        List<String> voteOptions,
        List<AutoPostSourceDto> citations,
        int version
) {
}
