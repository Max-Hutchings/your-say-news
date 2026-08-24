package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.VotingType;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("A sourced, balanced post draft for human review")
public record AgentDraftDto(
        @Description("Return exactly 3 neutral overview claims.")
        List<SourcedClaimDto> summaryClaims,
        @Description("Return exactly 2 strongest material claims supporting the motion.")
        List<SourcedClaimDto> caseForClaims,
        @Description("Return exactly 2 strongest material claims opposing the motion.")
        List<SourcedClaimDto> caseAgainstClaims,
        @Description("One neutral question of at most 20 words asking whether the reader supports the motion.")
        String supportQuestion,
        @Description("BINARY for a genuine Agree or Disagree motion; otherwise MULTIPLE_CHOICE.")
        VotingType votingType,
        @Description("For BINARY return exactly Agree then Disagree; otherwise return 2 to 5 neutral options of at most 6 words each.")
        List<AgentVoteOptionDto> voteOptions,
        @Description("Return 2 to 6 sources referenced by the claims, with no unused or duplicate sources.")
        List<AgentSourceDto> sources,
        @Description("One neutral factual image sentence of at most 25 words for a human editor.")
        String imageBrief,
        @Description("A search query of 3 to 8 words for an owned or reusable licensed image.")
        String imageSearchQuery
) {
    public AgentDraftDto(List<SourcedClaimDto> summaryClaims, List<SourcedClaimDto> caseForClaims,
                         List<SourcedClaimDto> caseAgainstClaims, String supportQuestion,
                         List<AgentSourceDto> sources, String imageBrief, String imageSearchQuery) {
        this(summaryClaims, caseForClaims, caseAgainstClaims, supportQuestion, VotingType.BINARY,
                List.of(new AgentVoteOptionDto("Agree"), new AgentVoteOptionDto("Disagree")),
                sources, imageBrief, imageSearchQuery);
    }
}
