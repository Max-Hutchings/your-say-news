package com.yoursay.feed;

import java.util.Set;

/**
 * The per-request signals a {@link FeedRanker} ranks <em>for</em>: who is viewing, and which authors
 * they follow (the follow set comes from the user-service {@code social} domain). Kept as a small
 * value object so richer ranking signals (votes, recency windows, interests) can be added later
 * without changing the {@link FeedRanker} method shape.
 *
 * @param userId            the viewer the feed is being built for
 * @param followedAuthorIds author ids this viewer follows; their posts get the MVP1 follow-boost
 */
public record FeedContext(Long userId, Set<Long> followedAuthorIds) {
}
