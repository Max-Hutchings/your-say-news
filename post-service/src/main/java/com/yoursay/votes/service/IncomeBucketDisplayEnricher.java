package com.yoursay.votes.service;

import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.user.usercharacteristic.IncomeRangeDisplayService;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.votes.dto.BucketSentiment;
import com.yoursay.votes.dto.CharacteristicSnapshot;
import com.yoursay.votes.dto.CohortAggregateV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import com.yoursay.votes.dto.SentimentBreakdownDto;
import com.yoursay.votes.error.VoteApiException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Adds real local range data to identity-free aggregates without changing their cohort keys. */
@ApplicationScoped
public class IncomeBucketDisplayEnricher {
    private static final String OPERATION = "enrich_income_display";

    @Inject
    IncomeRangeDisplayService incomeRanges;

    @Inject
    DomainMetrics metrics;

    public SentimentBreakdownDto enrich(SentimentBreakdownDto breakdown) {
        if (!isIncomeAxis(breakdown.characteristic())) {
            return breakdown;
        }
        return observe(() -> new SentimentBreakdownDto(
                breakdown.postId(), breakdown.votingType(), breakdown.characteristic(),
                breakdown.options(), breakdown.buckets().stream().map(this::enrich).toList(),
                breakdown.suppressedBuckets()));
    }

    public PostAnalysisAggregateV1 enrich(PostAnalysisAggregateV1 aggregate) {
        return observe(() -> {
            Map<String, IncomeRangeDisplayDto> resolved = new HashMap<>();
            return new PostAnalysisAggregateV1(
                    aggregate.schemaVersion(), aggregate.postId(), aggregate.votingType(), aggregate.summary(),
                    aggregate.question(), aggregate.jurisdiction(), aggregate.options(),
                    aggregate.canonicalVoteCount(), aggregate.aggregateVersion(), aggregate.capturedAt(),
                    aggregate.overall(), aggregate.cohorts().stream()
                            .map(cohort -> enrich(cohort, resolved)).toList(),
                    aggregate.metadata());
        });
    }

    private BucketSentiment enrich(BucketSentiment bucket) {
        if (CharacteristicSnapshot.UNKNOWN.equals(bucket.bucket())) {
            return bucket;
        }
        IncomeRangeDisplayDto income = resolveRequiredDisplay(bucket.bucket());
        return new BucketSentiment(
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
        return new CohortDimensionV1(
                dimension.axis(), dimension.bucket(), income.label(), income);
    }

    private IncomeRangeDisplayDto resolveOnce(
            String bucketId, Map<String, IncomeRangeDisplayDto> resolved) {
        return resolved.computeIfAbsent(bucketId, this::resolveRequiredDisplay);
    }

    private IncomeRangeDisplayDto resolveRequiredDisplay(String bucketId) {
        IncomeRangeDisplayDto income = incomeRanges.resolveDisplay(bucketId);
        if (income == null) {
            throw VoteApiException.unresolvedIncomeRange();
        }
        return income;
    }

    private <T> T observe(Supplier<T> enrichment) {
        long startedAt = System.nanoTime();
        try {
            T result = enrichment.get();
            recordOutcome("success", "none", "none", startedAt);
            return result;
        } catch (RuntimeException exception) {
            String errorCode = exception instanceof VoteApiException voteError
                    ? voteError.errorCode() : "INCOME_DISPLAY_ENRICHMENT_FAILED";
            recordOutcome("service_error", "data_integrity", errorCode, startedAt);
            throw exception;
        }
    }

    private void recordOutcome(
            String outcome, String errorType, String errorCode, long startedAt) {
        if (metrics != null) {
            metrics.recordOperation("votes", OPERATION, outcome, errorType, errorCode,
                    System.nanoTime() - startedAt);
        }
    }

    private static boolean isIncomeAxis(String axis) {
        return "personalIncomeRange".equals(axis) || "householdIncomeRange".equals(axis);
    }
}
