package com.yoursay.agent;

import java.util.List;

public record AgentDraftDto(
        List<SourcedClaimDto> summaryClaims,
        List<SourcedClaimDto> caseForClaims,
        List<SourcedClaimDto> caseAgainstClaims,
        String supportQuestion,
        List<AgentSourceDto> sources,
        String imageBrief,
        String imageSearchQuery
) {
}
