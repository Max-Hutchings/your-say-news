package com.yoursay.posts;

/**
 * How a media item is shaped, so the feed can size it deterministically without measuring the bytes:
 * {@code LANDSCAPE} renders in a fixed 16:9 box, {@code PORTRAIT} in a tall centred box (which then
 * collapses the summary to a "see more" line). Crosses the domain boundary (DTOs), so it lives at the
 * domain's public top level.
 */
public enum Orientation {
    LANDSCAPE,
    PORTRAIT
}
