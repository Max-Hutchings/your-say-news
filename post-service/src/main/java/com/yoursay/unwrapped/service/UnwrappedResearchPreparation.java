package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.selection.InsightSelectionService;
import com.yoursay.unwrapped.selection.UnwrappedAnalysisBriefV1;
import com.yoursay.votes.PostAnalysisAggregateService;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Shared aggregate capture and cohort-selection path for queued and benchmark generation. */
@ApplicationScoped
class UnwrappedResearchPreparation {
    @Inject
    PostAnalysisAggregateService aggregates;
    @Inject
    InsightSelectionService selector;

    PreparedResearch prepare(Long postId) {
        PostAnalysisAggregateV1 aggregate = aggregates.capture(postId);
        UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);
        UnwrappedResearchRequest request = new UnwrappedResearchRequest(
                brief.postId(), brief.summary(), brief.question(), brief.jurisdiction(),
                brief.canonicalVoteCount(), brief.aggregateVersion(), brief.options());
        return new PreparedResearch(aggregate, request);
    }

    record PreparedResearch(
            PostAnalysisAggregateV1 aggregate,
            UnwrappedResearchRequest request
    ) {
    }
}
