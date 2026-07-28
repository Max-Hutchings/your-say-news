package com.yoursay.unwrapped.selection;

import com.yoursay.votes.dto.CohortDimensionV1;
import java.util.List;

public record SelectedCohortV1(
        String cohortId,
        List<CohortDimensionV1> dimensions,
        CandidateRole role,
        String relevanceReason,
        long sampleSize,
        double populationSharePercentage,
        long optionVoteCount,
        double compositionPercentage,
        double propensityPercentage,
        double overIndexPercentagePoints,
        double differenceFromRestPercentagePoints,
        double wilson95Low,
        double wilson95High,
        double adjustedQValue
) {
}
