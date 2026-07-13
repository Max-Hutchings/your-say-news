package com.yoursay.feed;

import com.yoursay.posts.PostDto;
import io.smallrye.mutiny.Uni;

import java.util.List;

public interface FeedService {

    Uni<List<PostDto>> getFeed(String viewerEmail, String authorization, int page, int size,
                               FeedPostType postType);

    default Uni<List<PostDto>> getFeed(String viewerEmail, String authorization, int page, int size) {
        return getFeed(viewerEmail, authorization, page, size, null);
    }
}
