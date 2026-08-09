package com.yoursay.posts.dto;

import com.yoursay.posts.PostMediaFilter;

import java.time.Instant;

/**
 * One page of the post stream, addressed by keyset rather than offset: return the posts that come
 * strictly after {@code (cursorCreatedAt, cursorId)} in {@code createdAt desc, id desc} order.
 *
 * <p>Both cursor fields are null for the first page. Because the cursor names a row rather than a
 * count, posts published while a reader pages cannot shift the boundary and hide an unseen post.
 */
public record PostPageRequest(
        Instant cursorCreatedAt,
        Long cursorId,
        PostMediaFilter mediaFilter,
        /**
         * Restrict the page to posts carrying this canonical topic, or null for every topic
         * (ADR-043). Like the media filter this is a SQL predicate on the same scan, so the cursor
         * still names a row in {@code createdAt desc, id desc} and paging stays gap-free.
         */
        String topicId,
        int limit
) {
    /** The first page of the stream, unfiltered. */
    public static PostPageRequest first(int limit) {
        return new PostPageRequest(null, null, PostMediaFilter.ANY, null, limit);
    }
}
