package com.yoursay.feed;

import com.yoursay.feed.dto.FeedPage;
import io.smallrye.mutiny.Uni;

public interface FeedService {

    /**
     * One page of the viewer's feed. {@code cursor} is null for the first page and otherwise the
     * {@code nextCursor} of the previous page; {@code size} is normalised and capped server-side.
     *
     * <p>{@code topicTagId} restricts the page to one governed tag for a category feed. It
     * changes only which candidates are considered: ordering, ranking and cursor semantics are
     * identical to the unfiltered feed. Null means every topic.
     */
    Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size,
                          FeedPostType postType, String topicTagId);

    default Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size,
                                  FeedPostType postType) {
        return getFeed(viewerEmail, authorization, cursor, size, postType, null);
    }

    default Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size) {
        return getFeed(viewerEmail, authorization, cursor, size, null, null);
    }
}
