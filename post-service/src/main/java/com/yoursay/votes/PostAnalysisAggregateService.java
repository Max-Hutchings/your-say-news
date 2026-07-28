package com.yoursay.votes;

import com.yoursay.votes.dto.PostAnalysisAggregateV1;

/** Public aggregate-only boundary consumed by the Unwrapped domain. */
public interface PostAnalysisAggregateService {
    PostAnalysisAggregateV1 capture(Long postId);
}
