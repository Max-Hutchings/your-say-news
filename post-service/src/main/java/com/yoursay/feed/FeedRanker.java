package com.yoursay.feed;

import java.util.List;

/**
 * Public contract for feed ordering. Given the viewer's {@link FeedContext} and a set of candidate
 * posts, return the post ids in the order they should appear.
 *
 * <p>This is the seam that keeps the real recommendation engine a drop-in replacement: MVP1 ships
 * {@code ChronologicalFollowBoostRanker} (reverse-chronological, boosting followed authors), and a
 * smarter ranker later implements the same interface with no change to feed assembly or callers.
 *
 * <p>Pure and synchronous by design — it ranks candidates handed to it and performs no I/O. Gathering
 * the candidates and the follow set (the async feed-assembly layer) is a separate concern, landing in
 * Stage 5.
 */
public interface FeedRanker {

    /**
     * Order {@code candidates} for the given context, returning their post ids best-first.
     * Implementations must not mutate the inputs and must return every candidate's id exactly once.
     */
    List<Long> rank(FeedContext context, List<RankablePost> candidates);
}
