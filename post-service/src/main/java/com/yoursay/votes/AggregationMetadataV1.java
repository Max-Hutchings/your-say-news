package com.yoursay.votes;

public record AggregationMetadataV1(
        String ruleSetVersion,
        int suppressBelow,
        int minimumOverallSample,
        int minimumCohortSample,
        int minimumIntersectionSample,
        double minimumCohortSharePercentage,
        double minimumEffectPercentagePoints,
        double falseDiscoveryRate,
        long suppressedCohorts,
        long testedComparisons
) {
}
