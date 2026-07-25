package com.yoursay.unwrapped;

import com.yoursay.posts.VoteOptionDto;
import java.util.List;

public record OptionBriefV1(
        VoteOptionDto option,
        long overallVoteCount,
        double overallVotePercentage,
        List<SelectedCohortV1> candidates,
        List<String> researchQuestions,
        List<String> prohibitedInferences,
        String insufficientEvidence
) {
}
