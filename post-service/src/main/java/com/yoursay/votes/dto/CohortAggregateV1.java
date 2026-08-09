package com.yoursay.votes.dto;

import java.util.List;

public record CohortAggregateV1(
        String cohortId,
        List<CohortDimensionV1> dimensions,
        MembershipSemantics membershipSemantics,
        long sampleSize,
        double populationSharePercentage,
        List<OptionStatisticV1> options
) {
}
