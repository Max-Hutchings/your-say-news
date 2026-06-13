package com.yoursay.feed;

import java.time.Instant;

/**
 * The minimal view of a post a {@link FeedRanker} needs to order it: its id, who wrote it (for
 * follow-boost), and when it was posted (for recency). Deliberately tiny so the ranker stays a pure
 * ordering function over candidates and the real rec engine can drop in behind the same interface.
 *
 * @param postId   the post to rank
 * @param authorId the post's author, matched against {@link FeedContext#followedAuthorIds()}
 * @param postedAt when the post was published, for reverse-chronological ordering
 */
public record RankablePost(Long postId, Long authorId, Instant postedAt) {
}
