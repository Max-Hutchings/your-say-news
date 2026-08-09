package com.yoursay.feed.dto;

import com.yoursay.posts.dto.PostDto;

import java.util.List;

/**
 * One page of the reader's feed: the posts in ranked order, plus the token that fetches the page
 * after it. {@code nextCursor} is null when this page is the end of the feed — the page being
 * shorter than the requested size does not mean the end, since the candidate query fills a page
 * whenever matching posts remain.
 */
public record FeedPage(List<PostDto> posts, String nextCursor) {
}
