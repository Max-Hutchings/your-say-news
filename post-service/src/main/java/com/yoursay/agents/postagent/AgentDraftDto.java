package com.yoursay.agents.postagent;

import com.yoursay.posts.VotingType;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("A sourced, balanced post draft for human review")
public record AgentDraftDto(
        @Description("Neutral factual overview claims")
        List<SourcedClaimDto> summaryClaims,
        @Description("Strongest material claims supporting the motion")
        List<SourcedClaimDto> caseForClaims,
        @Description("Strongest material claims opposing the motion")
        List<SourcedClaimDto> caseAgainstClaims,
        @Description("One concise, neutral question asking whether the reader supports the motion")
        String supportQuestion,
        @Description("BINARY for an Agree/Disagree motion or MULTIPLE_CHOICE when several distinct answers are useful")
        VotingType votingType,
        @Description("Agree and Disagree for BINARY, or two to five ordered neutral answers for MULTIPLE_CHOICE")
        List<AgentVoteOptionDto> voteOptions,
        @Description("Every source referenced by any claim, with no unused sources")
        List<AgentSourceDto> sources,
        @Description("A neutral factual image brief for a human editor")
        String imageBrief,
        @Description("A search query to help a human find an owned or reusable licensed image")
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
