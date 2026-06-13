package com.yoursay.votes;

import java.util.List;

/**
 * The public "how different kinds of people feel about this" result for one post, sliced along a
 * single characteristic axis. This is the only shape the votes domain exposes for sentiment — it
 * is aggregate-by-construction, so no caller can reach an individual vote through it.
 *
 * @param postId            the post these tallies are for
 * @param characteristic    the axis the breakdown is sliced by (e.g. {@code "politicalPersuasion"}),
 *                          or {@link #OVERALL} for the single all-voters tally
 * @param buckets           one {@link BucketSentiment} per surfaced bucket
 * @param suppressedBuckets how many buckets existed but were withheld because their total fell below
 *                          the {@code k} suppression threshold (see roadmap privacy fast-follow).
 *                          {@code 0} under the MVP1 default of {@code k=0}.
 */
public record SentimentBreakdownDto(
        Long postId,
        String characteristic,
        List<BucketSentiment> buckets,
        long suppressedBuckets
) {
    /** The {@code characteristic} value used for the overall (not-sliced) tally. */
    public static final String OVERALL = "OVERALL";
}
