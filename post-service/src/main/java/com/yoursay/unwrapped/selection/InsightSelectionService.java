package com.yoursay.unwrapped.selection;

import com.yoursay.votes.dto.PostAnalysisAggregateV1;

public interface InsightSelectionService {
    UnwrappedAnalysisBriefV1 select(PostAnalysisAggregateV1 aggregate);
}
