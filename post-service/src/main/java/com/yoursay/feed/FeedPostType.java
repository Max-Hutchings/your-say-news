package com.yoursay.feed;

import com.yoursay.posts.MediaType;
import com.yoursay.posts.PostDto;

/** The reader-facing feed split: video posts, or article posts made from text and images. */
public enum FeedPostType {
    VIDEO,
    ARTICLE;

    public boolean matches(PostDto post) {
        boolean hasVideo = post.media() != null
                && post.media().stream().anyMatch(media -> media.mediaType() == MediaType.VIDEO);
        return this == VIDEO ? hasVideo : !hasVideo;
    }
}
