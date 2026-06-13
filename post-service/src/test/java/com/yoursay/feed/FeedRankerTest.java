package com.yoursay.feed;

import com.yoursay.feed.service.ChronologicalFollowBoostRanker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the MVP1 feed ranker orders posts as the roadmap specifies: followed authors boosted above
 * the rest, newest-first within each group. Pure ordering function, so the expected order is pinned
 * exactly.
 */
class FeedRankerTest {

    private final ChronologicalFollowBoostRanker ranker = new ChronologicalFollowBoostRanker();

    private static final Instant NOW = Instant.parse("2026-06-13T12:00:00Z");

    private static RankablePost post(long id, long author, long hoursAgo) {
        return new RankablePost(id, author, NOW.minus(hoursAgo, ChronoUnit.HOURS));
    }

    @Test
    void followedAuthorsBoostedAboveOthers_newestFirstWithinEachGroup() {
        // Viewer follows author 10. Mix of followed/unfollowed at different ages.
        FeedContext context = new FeedContext(1L, Set.of(10L));
        List<RankablePost> candidates = List.of(
                post(1, 10, 2),  // followed, 2h old
                post(2, 20, 0),  // not followed, newest overall
                post(3, 10, 1),  // followed, 1h old (newer than post 1)
                post(4, 30, 3)   // not followed, oldest
        );

        // Followed group newest-first (3 then 1), then unfollowed newest-first (2 then 4).
        assertEquals(List.of(3L, 1L, 2L, 4L), ranker.rank(context, candidates));
    }

    @Test
    void noFollows_isPureReverseChronological() {
        FeedContext context = new FeedContext(1L, Set.of());
        List<RankablePost> candidates = List.of(
                post(1, 10, 5),
                post(2, 20, 1),
                post(3, 30, 9)
        );

        assertEquals(List.of(2L, 1L, 3L), ranker.rank(context, candidates));
    }

    @Test
    void nullTimestampSortsLast() {
        FeedContext context = new FeedContext(1L, Set.of());
        List<RankablePost> candidates = List.of(
                new RankablePost(1L, 10L, null),
                post(2, 20, 1)
        );

        assertEquals(List.of(2L, 1L), ranker.rank(context, candidates));
    }
}
