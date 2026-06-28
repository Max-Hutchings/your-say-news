package com.yoursay.votes;

/**
 * Public contract for aggregated, anonymised vote sentiment — the heart of the product.
 *
 * <p>Implementations return <strong>only</strong> {@link SentimentBreakdownDto} (counts and
 * percentages); they must never expose a path from an aggregate back to an individual vote or
 * identity. Aggregation slices by the voter's {@link CharacteristicSnapshot} captured at vote
 * time, so it never query-time cross-joins into user-service.
 *
 * <p>A {@code k}-anonymity suppression threshold sits behind this interface
 * ({@code votes.aggregation.suppress-below}, default {@code 0} for MVP1). Buckets whose total
 * falls below {@code k} are withheld and counted in {@link SentimentBreakdownDto#suppressedBuckets()}.
 * Raising {@code k} is the single config flip that hardens against small-bucket re-identification.
 *
 * <p>All methods are blocking and run on virtual threads.
 */
public interface SentimentAggregator {

    /** Overall yes/no split for a post as a single {@link SentimentBreakdownDto#OVERALL} bucket. */
    SentimentBreakdownDto overallSentiment(Long postId);

    /**
     * Yes/no split for a post broken down by one characteristic axis (a {@link CharacteristicSnapshot}
     * field name, e.g. {@code "politicalPersuasion"}), one bucket per distinct value, with the
     * {@code k}-suppression threshold applied.
     */
    SentimentBreakdownDto sentimentByCharacteristic(Long postId, String characteristic);
}
