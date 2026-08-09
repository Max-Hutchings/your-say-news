package com.yoursay.posts;

/**
 * How a candidate page filters posts by the media they carry. Applied as a SQL predicate by the
 * repository, so a filtered page is full whenever matching posts remain — callers never have to
 * over-fetch and discard.
 */
public enum PostMediaFilter {
    /** No media constraint. */
    ANY,
    /** Only posts carrying at least one video. */
    WITH_VIDEO,
    /** Only posts carrying no video (text and images). */
    WITHOUT_VIDEO
}
