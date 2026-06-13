package com.yoursay.votes;

/**
 * Aggregated yes/no sentiment for a single characteristic bucket (e.g. all {@code LEFT} voters,
 * or all voters overall). <strong>Counts and percentages only — never a user id or any row that
 * could re-identify an individual.</strong> This record is the atom of the privacy contract: if a
 * field here could name a person, the contract is broken.
 *
 * @param bucket   the bucket label (an enum name like {@code "LEFT"}, or {@code "OVERALL"})
 * @param yesCount voters in this bucket who voted yes on the support question
 * @param noCount  voters in this bucket who voted no
 * @param total    {@code yesCount + noCount}
 * @param yesPct   yes share of this bucket, 0..100 ({@code 0} when {@code total == 0})
 * @param noPct    no share of this bucket, 0..100 ({@code 0} when {@code total == 0})
 */
public record BucketSentiment(
        String bucket,
        long yesCount,
        long noCount,
        long total,
        double yesPct,
        double noPct
) {
    public static BucketSentiment of(String bucket, long yesCount, long noCount) {
        long total = yesCount + noCount;
        double yesPct = total == 0 ? 0.0 : (100.0 * yesCount) / total;
        double noPct = total == 0 ? 0.0 : (100.0 * noCount) / total;
        return new BucketSentiment(bucket, yesCount, noCount, total, yesPct, noPct);
    }
}
