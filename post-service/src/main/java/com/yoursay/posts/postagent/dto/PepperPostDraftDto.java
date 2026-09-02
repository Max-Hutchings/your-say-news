package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.VotingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The editable post fields produced by Pepper and persisted as the publisher's working copy. */
public record PepperPostDraftDto(
        @NotBlank @Size(max = 4000) String summary,
        @NotBlank @Size(max = 512) String supportQuestion,
        @Size(max = 512) String caseFor,
        @Size(max = 512) String caseAgainst,
        @NotNull VotingType votingType,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 120) String> voteOptions,
        @NotNull @Size(max = 20) List<@NotNull @Valid AgentSourceDto> citations
) {
}
