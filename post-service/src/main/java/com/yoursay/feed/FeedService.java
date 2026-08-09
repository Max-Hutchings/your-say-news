package com.yoursay.feed;

import com.yoursay.feed.dto.FeedPage;
import io.smallrye.mutiny.Uni;

public interface FeedService {

    /**
     * One page of the viewer's feed. {@code cursor} is null for the first page and otherwise the
     * {@code nextCursor} of the previous page; {@code size} is normalised and capped server-side.
     */
    Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size,
                          FeedPostType postType);

    default Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size) {
        return getFeed(viewerEmail, authorization, cursor, size, null);
    }
}
