package com.yoursay.feed.service;

import com.yoursay.feed.FeedContext;
import com.yoursay.feed.FeedRanker;
import com.yoursay.feed.RankablePost;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * MVP1 {@link FeedRanker}: reverse-chronological, with a follow-boost. Posts by authors the viewer
 * follows are surfaced above the rest; within each group the newest post comes first. This is the
 * deliberately simple baseline the roadmap calls for — the real recommendation engine replaces it by
 * implementing {@link FeedRanker}, nothing else changes.
 */
@ApplicationScoped
public class ChronologicalFollowBoostRanker implements FeedRanker {

    @Override
    public List<Long> rank(FeedContext context, List<RankablePost> candidates) {
        Set<Long> followed = context == null || context.followedAuthorIds() == null
                ? Set.of()
                : context.followedAuthorIds();

        // Followed authors first (boost), then newest first within each group. EPOCH as a null-safe
        // floor so a post missing a timestamp sorts last rather than throwing.
        Comparator<RankablePost> order = Comparator
                .comparing((RankablePost p) -> followed.contains(p.authorId()) ? 0 : 1)
                .thenComparing(p -> p.postedAt() == null ? Instant.EPOCH : p.postedAt(),
                        Comparator.reverseOrder());

        return candidates.stream()
                .sorted(order)
                .map(RankablePost::postId)
                .toList();
    }
}
