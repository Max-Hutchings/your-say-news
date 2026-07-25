package com.yoursay.unwrapped;

import com.yoursay.votes.PostAnalysisAggregateV1;

public interface InsightSelectionService {
    UnwrappedAnalysisBriefV1 select(PostAnalysisAggregateV1 aggregate);
}
