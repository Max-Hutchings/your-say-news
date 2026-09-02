package com.yoursay.feed.service;

import com.yoursay.feed.FeedPostType;
import com.yoursay.feed.FeedRanker;
import com.yoursay.feed.FeedService;
import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.feed.dto.FeedContext;
import com.yoursay.feed.dto.FeedPage;
import com.yoursay.feed.dto.RankablePost;
import com.yoursay.posts.PostService;
import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.dto.PostPageRequest;
import com.yoursay.posts.error.PostApiException;
import com.yoursay.topics.TopicService;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles one page of the feed: resolve the viewer and their follow set, fetch a keyset page of
 * candidates, and let {@link FeedRanker} order that page (ADR-042).
 *
 * <p>Two orders are in play and they are deliberately kept apart. The <em>scan</em> order is
 * {@code createdAt desc, id desc} — that is what the cursor names, and it is immutable, so paging is
 * gap-free even while posts are being published. The <em>ranked</em> order is whatever the ranker
 * returns, and it only ever reorders posts within a page. Deriving the next cursor from the ranked
 * output instead of the scan tail would skip posts.
 */
@ApplicationScoped
public class FeedServiceImpl implements FeedService {

    static final int DEFAULT_PAGE_SIZE = 5;
    static final int MAX_PAGE_SIZE = 50;

    @Inject
    PostService postService;

    @Inject
    FeedRanker ranker;

    @Inject
    FeedUserClient userClient;

    @Inject
    SocialClient socialClient;

    @Inject
    TopicService topicService;

    @Override
    public Uni<FeedPage> getFeed(String viewerEmail, String authorization, String cursor, int size,
                                 FeedPostType postType, String topicTagId) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        // Decoded up front so a bad token fails with 400 before any query runs.
        FeedCursor position = FeedCursor.decode(cursor);
        String topicTag = topicTagId == null || topicTagId.isBlank() ? null : topicTagId.trim();

        Uni<FeedUserClient.UserRef> viewer = userClient.getUserByEmail(viewerEmail, authorization)
                .onItem().ifNull().failWith(() -> PostApiException.unknownAuthor(viewerEmail));
        Uni<SocialClient.FollowingRef> following = socialClient.getFollowing(authorization);
        Uni<List<PostDto>> candidates = requireKnownTopic(topicTag)
                .chain(() -> postService.findPage(
                        candidateRequest(position, postType, topicTag, pageSize)));

        return Uni.combine().all().unis(viewer, following, candidates).asTuple()
                .map(tuple -> assemblePage(tuple.getItem1().id(), followedUserIds(tuple.getItem2()),
                        tuple.getItem3(), pageSize));
    }

    /**
     * Asks for one row beyond the page: its presence is what tells us more of the feed exists. A
     * short page can no longer mean "the end", because the type filter runs in SQL and fills pages.
     */
    private static PostPageRequest candidateRequest(FeedCursor position, FeedPostType postType,
                                                    String topicTag, int pageSize) {
        return new PostPageRequest(
                position == null ? null : position.createdAt(),
                position == null ? null : position.postId(),
                FeedPostType.mediaFilterFor(postType),
                topicTag,
                pageSize + 1);
    }

    /**
     * An unknown topic is a client bug, so it fails before the scan rather than returning an empty
     * page that a reader would read as a dead category.
     */
    private Uni<Void> requireKnownTopic(String topicTag) {
        return topicTag == null ? Uni.createFrom().voidItem() : topicService.requireExists(topicTag);
    }

    private static Set<Long> followedUserIds(SocialClient.FollowingRef following) {
        return following == null || following.userIds() == null ? Set.of() : following.userIds();
    }

    /** Trims the look-ahead row off the page, then ranks what remains and cuts the next cursor. */
    private FeedPage assemblePage(Long viewerId, Set<Long> followed, List<PostDto> fetched,
                                  int pageSize) {
        boolean hasMore = fetched.size() > pageSize;
        List<PostDto> page = hasMore ? fetched.subList(0, pageSize) : fetched;
        return new FeedPage(rank(viewerId, followed, page), nextCursor(page, hasMore));
    }

    private List<PostDto> rank(Long viewerId, Set<Long> followed, List<PostDto> page) {
        List<RankablePost> rankables = page.stream()
                .map(post -> new RankablePost(post.id(), post.userId(), post.createdAt()))
                .toList();
        Map<Long, PostDto> byId = page.stream().collect(Collectors.toMap(PostDto::id, post -> post));
        return ranker.rank(new FeedContext(viewerId, followed), rankables).stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** The scan position of the page's last candidate — never of its last ranked post. */
    private static String nextCursor(List<PostDto> page, boolean hasMore) {
        if (!hasMore || page.isEmpty()) {
            return null;
        }
        PostDto tail = page.get(page.size() - 1);
        return FeedCursor.encode(tail.createdAt(), tail.id());
    }
}
