package com.yoursay.votes.service;

import com.yoursay.user.usercharacteristic.IncomeRangeDisplayService;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.votes.dto.BucketSentiment;
import com.yoursay.votes.dto.CohortAggregateV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import com.yoursay.votes.dto.SentimentBreakdownDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/** Adds real local range data to identity-free aggregates without changing their cohort keys. */
@ApplicationScoped
public class IncomeBucketDisplayEnricher {

    @Inject
    IncomeRangeDisplayService incomeRanges;

    public SentimentBreakdownDto enrich(SentimentBreakdownDto breakdown) {
        if (!isIncomeAxis(breakdown.characteristic())) {
            return breakdown;
        }
        return new SentimentBreakdownDto(
                breakdown.postId(), breakdown.votingType(), breakdown.characteristic(),
                breakdown.options(), breakdown.buckets().stream().map(this::enrich).toList(),
                breakdown.suppressedBuckets());
    }

    public PostAnalysisAggregateV1 enrich(PostAnalysisAggregateV1 aggregate) {
        Map<String, IncomeRangeDisplayDto> resolved = new HashMap<>();
        return new PostAnalysisAggregateV1(
                aggregate.schemaVersion(), aggregate.postId(), aggregate.votingType(), aggregate.summary(),
                aggregate.question(), aggregate.jurisdiction(), aggregate.options(),
                aggregate.canonicalVoteCount(), aggregate.aggregateVersion(), aggregate.capturedAt(),
                aggregate.overall(), aggregate.cohorts().stream()
                        .map(cohort -> enrich(cohort, resolved)).toList(),
                aggregate.metadata());
    }

    private BucketSentiment enrich(BucketSentiment bucket) {
        IncomeRangeDisplayDto income = incomeRanges.resolveDisplay(bucket.bucket());
        return income == null ? bucket : new BucketSentiment(
                bucket.bucket(), income.label(), income, bucket.total(), bucket.choices());
    }

    private CohortAggregateV1 enrich(
            CohortAggregateV1 cohort, Map<String, IncomeRangeDisplayDto> resolved) {
        return new CohortAggregateV1(
                cohort.cohortId(), cohort.dimensions().stream()
                        .map(dimension -> enrich(dimension, resolved)).toList(),
                cohort.membershipSemantics(), cohort.sampleSize(),
                cohort.populationSharePercentage(), cohort.options());
    }

    private CohortDimensionV1 enrich(
            CohortDimensionV1 dimension, Map<String, IncomeRangeDisplayDto> resolved) {
        if (!isIncomeAxis(dimension.axis())) {
            return dimension;
        }
        IncomeRangeDisplayDto income = resolveOnce(dimension.bucket(), resolved);
        return income == null ? dimension
                : new CohortDimensionV1(dimension.axis(), dimension.bucket(), income.label(), income);
    }

    private IncomeRangeDisplayDto resolveOnce(
            String bucketId, Map<String, IncomeRangeDisplayDto> resolved) {
        if (!resolved.containsKey(bucketId)) {
            resolved.put(bucketId, incomeRanges.resolveDisplay(bucketId));
        }
        return resolved.get(bucketId);
    }

    private static boolean isIncomeAxis(String axis) {
        return "personalIncomeRange".equals(axis) || "householdIncomeRange".equals(axis);
    }
}
