package com.yoursay.agent;

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
        @Description("Every source referenced by any claim, with no unused sources")
        List<AgentSourceDto> sources,
        @Description("A neutral factual image brief for a human editor")
        String imageBrief,
        @Description("A search query to help a human find an owned or reusable licensed image")
        String imageSearchQuery
) {
}
