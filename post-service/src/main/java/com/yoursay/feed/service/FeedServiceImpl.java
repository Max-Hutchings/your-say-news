package com.yoursay.feed.service;

import com.yoursay.feed.FeedContext;
import com.yoursay.feed.FeedRanker;
import com.yoursay.feed.FeedService;
import com.yoursay.feed.FeedPostType;
import com.yoursay.feed.RankablePost;
import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.posts.PostDto;
import com.yoursay.posts.PostService;
import com.yoursay.posts.error.PostApiException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class FeedServiceImpl implements FeedService {

    static final int DEFAULT_PAGE_SIZE = 5;
    static final int MAX_PAGE_SIZE = 50;
    static final int MAX_RANKING_WINDOW = 250;

    @Inject
    PostService postService;

    @Inject
    FeedRanker ranker;

    @RestClient
    FeedUserClient userClient;

    @RestClient
    SocialClient socialClient;

    @Override
    public Uni<List<PostDto>> getFeed(String viewerEmail, String authorization, int page, int size,
                                      FeedPostType postType) {
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int requested = MAX_RANKING_WINDOW;

        Uni<FeedUserClient.UserRef> viewer = userClient.getUserByEmail(viewerEmail, authorization)
                .onItem().ifNull().failWith(() -> PostApiException.unknownAuthor(viewerEmail));
        Uni<SocialClient.FollowingRef> following = socialClient.getFollowing(authorization);
        Uni<List<PostDto>> candidates = postService.getRecent(0, requested);

        return Uni.combine().all().unis(viewer, following, candidates).asTuple()
                .map(tuple -> {
                    Long viewerId = tuple.getItem1().id();
                    Set<Long> followed = tuple.getItem2() == null || tuple.getItem2().userIds() == null
                            ? Set.of()
                            : tuple.getItem2().userIds();
                    List<PostDto> posts = tuple.getItem3();
                    List<RankablePost> rankables = posts.stream()
                            .map(p -> new RankablePost(p.id(), p.userId(), p.createdAt()))
                            .toList();
                    List<Long> rankedIds = ranker.rank(new FeedContext(viewerId, followed), rankables);
                    Map<Long, PostDto> byId = posts.stream()
                            .collect(Collectors.toMap(PostDto::id, p -> p));
                    return rankedIds.stream()
                            .map(byId::get)
                            .filter(Objects::nonNull)
                            .filter(post -> postType == null || postType.matches(post))
                            .skip((long) safePage * safeSize)
                            .limit(safeSize)
                            .toList();
                });
    }
}
