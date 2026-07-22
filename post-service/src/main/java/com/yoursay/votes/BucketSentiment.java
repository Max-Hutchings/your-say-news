package com.yoursay.votes;

/**
 * Aggregated option sentiment for a single characteristic bucket (e.g. all {@code LEFT} voters,
 * or all voters overall). <strong>Counts and percentages only — never a user id or any row that
 * could re-identify an individual.</strong> This record is the atom of the privacy contract: if a
 * field here could name a person, the contract is broken.
 *
 * @param bucket   the bucket label (an enum name like {@code "LEFT"}, or {@code "OVERALL"})
 * @param total total canonical votes in the bucket
 * @param choices stable option counts and percentages, in the post's option order
 */
public record BucketSentiment(String bucket, long total, java.util.List<ChoiceSentiment> choices) {
}
