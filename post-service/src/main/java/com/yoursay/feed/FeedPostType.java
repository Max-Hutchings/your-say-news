package com.yoursay.feed;

import com.yoursay.posts.PostMediaFilter;

/** The reader-facing feed split: video posts, or article posts made from text and images. */
public enum FeedPostType {
    VIDEO,
    ARTICLE;

    /**
     * The candidate-source filter this split maps to. The filter runs as a SQL predicate, so a
     * filtered feed pages past non-matching posts instead of ending early on them.
     */
    public PostMediaFilter toMediaFilter() {
        return this == VIDEO ? PostMediaFilter.WITH_VIDEO : PostMediaFilter.WITHOUT_VIDEO;
    }

    /** Null-safe form: no selected split means no media constraint. */
    public static PostMediaFilter mediaFilterFor(FeedPostType postType) {
        return postType == null ? PostMediaFilter.ANY : postType.toMediaFilter();
    }
}
