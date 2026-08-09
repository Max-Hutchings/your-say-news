package com.yoursay.feed.service;

import com.yoursay.feed.FeedPostType;
import com.yoursay.feed.FeedRanker;
import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.feed.dto.FeedContext;
import com.yoursay.feed.dto.FeedPage;
import com.yoursay.feed.dto.RankablePost;
import com.yoursay.feed.error.FeedApiException;
import com.yoursay.posts.MediaType;
import com.yoursay.posts.Orientation;
import com.yoursay.posts.PostMediaFilter;
import com.yoursay.posts.PostService;
import com.yoursay.posts.dto.CreatePostRequest;
import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.dto.PostMediaDto;
import com.yoursay.posts.dto.PostPageRequest;
import com.yoursay.posts.dto.PresignRequest;
import com.yoursay.posts.dto.PresignResponse;
import com.yoursay.posts.error.PostApiException;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Feed assembly over a keyset-paged candidate source (ADR-042). The behaviour that matters here is
 * that the page handed to the ranker and the cursor handed to the client come from two different
 * orders — the cursor tracks the candidate scan position ({@code createdAt, id}), while the returned
 * posts are in ranked order — and that the end of the feed is detected by probing for one extra row
 * rather than by a short page.
 */
class FeedServiceImplTest {

    private static final String VIEWER = "viewer@example.com";
    private static final String TOKEN = "Bearer token";

    @Test
    void ranksOnlyTheRequestedPageAndAnchorsTheCursorToItsChronologicalTail() {
        // Post 3 is both the oldest post on the page AND the followed author's, so the ranker lifts
        // it to the *front*. That splits the two orders: the scan tail is post 3 while the ranked
        // tail is post 2. The cursor must name the scan tail — deriving it from the ranked output
        // would hand back post 2's position and skip post 3's successors on the next page.
        FeedFixture fixture = fixture(List.of(
                post(1, 20, "2026-07-09T12:00:00Z"),
                post(2, 20, "2026-07-09T11:00:00Z"),
                post(3, 10, "2026-07-09T10:00:00Z"),
                post(4, 20, "2026-07-09T09:00:00Z")));

        FeedPage page = fixture.service.getFeed(VIEWER, TOKEN, null, 3, null).await().indefinitely();

        assertEquals(List.of(3L, 1L, 2L), page.posts().stream().map(PostDto::id).toList());
        assertEquals(FeedCursor.encode(Instant.parse("2026-07-09T10:00:00Z"), 3L), page.nextCursor());
        assertNotEquals(FeedCursor.encode(Instant.parse("2026-07-09T11:00:00Z"), 2L),
                page.nextCursor(), "cursor must follow the candidate scan, not the ranked order");
    }

    @Test
    void probesOneRowBeyondThePageToDetectTheEndOfTheFeed() {
        FeedFixture fixture = fixture(List.of(
                post(1, 20, "2026-07-09T12:00:00Z"),
                post(2, 20, "2026-07-09T11:00:00Z"),
                post(3, 20, "2026-07-09T10:00:00Z")));

        FeedPage page = fixture.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();

        assertEquals(3, fixture.postService.lastRequest.limit());
        assertEquals(List.of(1L, 2L), page.posts().stream().map(PostDto::id).toList());
    }

    @Test
    void aFullPageWithNothingBehindItIsTheEndOfTheFeed() {
        // Exactly two posts exist and exactly two are asked for. A "short page means the end"
        // implementation would hand back a cursor here and make the client request an empty page.
        FeedFixture fixture = fixture(List.of(
                post(1, 20, "2026-07-09T12:00:00Z"),
                post(2, 20, "2026-07-09T11:00:00Z")));

        FeedPage page = fixture.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();

        assertEquals(List.of(1L, 2L), page.posts().stream().map(PostDto::id).toList());
        assertNull(page.nextCursor());
    }

    @Test
    void pagingByCursorCoversEveryPostExactlyOnce() {
        FeedFixture fixture = fixture(LongStream.rangeClosed(1, 7)
                .mapToObj(id -> post(id, 20, Instant.parse("2026-07-09T12:00:00Z")
                        .minus(id, ChronoUnit.MINUTES)))
                .toList());

        FeedPage first = fixture.service.getFeed(VIEWER, TOKEN, null, 3, null).await().indefinitely();
        FeedPage second = fixture.service.getFeed(VIEWER, TOKEN, first.nextCursor(), 3, null)
                .await().indefinitely();
        FeedPage third = fixture.service.getFeed(VIEWER, TOKEN, second.nextCursor(), 3, null)
                .await().indefinitely();

        assertEquals(List.of(1L, 2L, 3L), first.posts().stream().map(PostDto::id).toList());
        assertEquals(List.of(4L, 5L, 6L), second.posts().stream().map(PostDto::id).toList());
        assertEquals(List.of(7L), third.posts().stream().map(PostDto::id).toList());
        assertNull(third.nextCursor());
    }

    @Test
    void aPostPublishedMidScrollCannotPushAnUnseenPostPastThePageBoundary() {
        // The defect offset paging had: a newer post shifts every later offset by one, so the post
        // that was at the boundary is never served. The cursor is anchored to a row, not a count.
        FeedFixture fixture = fixture(LongStream.rangeClosed(1, 4)
                .mapToObj(id -> post(id, 20, Instant.parse("2026-07-09T12:00:00Z")
                        .minus(id, ChronoUnit.MINUTES)))
                .toList());

        FeedPage first = fixture.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();
        fixture.postService.publish(post(99, 20, "2026-07-09T13:00:00Z"));
        FeedPage second = fixture.service.getFeed(VIEWER, TOKEN, first.nextCursor(), 2, null)
                .await().indefinitely();

        assertEquals(List.of(1L, 2L), first.posts().stream().map(PostDto::id).toList());
        assertEquals(List.of(3L, 4L), second.posts().stream().map(PostDto::id).toList());
    }

    @Test
    void resumesTheCandidateScanFromTheDecodedCursor() {
        FeedFixture fixture = fixture(List.of(
                post(1, 20, "2026-07-09T12:00:00Z"),
                post(2, 20, "2026-07-09T11:00:00Z")));

        fixture.service.getFeed(VIEWER, TOKEN,
                        FeedCursor.encode(Instant.parse("2026-07-09T12:00:00Z"), 1L), 5, null)
                .await().indefinitely();

        PostPageRequest request = fixture.postService.lastRequest;
        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), request.cursorCreatedAt());
        assertEquals(1L, request.cursorId());
    }

    @Test
    void translatesThePostTypeIntoACandidateSourceFilter() {
        FeedFixture videos = fixture(List.of(postWithMedia(1, 20, "2026-07-09T12:00:00Z", MediaType.VIDEO)));
        FeedFixture articles = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));
        FeedFixture everything = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));

        videos.service.getFeed(VIEWER, TOKEN, null, 5, FeedPostType.VIDEO).await().indefinitely();
        articles.service.getFeed(VIEWER, TOKEN, null, 5, FeedPostType.ARTICLE).await().indefinitely();
        everything.service.getFeed(VIEWER, TOKEN, null, 5, null).await().indefinitely();

        assertEquals(PostMediaFilter.WITH_VIDEO, videos.postService.lastRequest.mediaFilter());
        assertEquals(PostMediaFilter.WITHOUT_VIDEO, articles.postService.lastRequest.mediaFilter());
        assertEquals(PostMediaFilter.ANY, everything.postService.lastRequest.mediaFilter());
    }

    @Test
    void aTypeFilteredFeedKeepsPagingPastANonMatchingRun() {
        // The false end-of-feed this ADR removes: filtering in SQL means a page is full whenever
        // matching posts remain, however many non-matching posts sit between them.
        FeedFixture fixture = fixture(List.of(
                postWithMedia(1, 20, "2026-07-09T12:00:00Z", MediaType.VIDEO),
                post(2, 20, "2026-07-09T11:00:00Z"),
                post(3, 20, "2026-07-09T10:00:00Z"),
                post(4, 20, "2026-07-09T09:00:00Z"),
                postWithMedia(5, 20, "2026-07-09T08:00:00Z", MediaType.IMAGE, MediaType.VIDEO)));

        FeedPage page = fixture.service.getFeed(VIEWER, TOKEN, null, 2, FeedPostType.VIDEO)
                .await().indefinitely();

        assertEquals(List.of(1L, 5L), page.posts().stream().map(PostDto::id).toList());
        assertNull(page.nextCursor());
    }

    @Test
    void normalizesAnAbsentSizeAndCapsAnOversizedOne() {
        FeedFixture defaulted = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));
        FeedFixture oversized = fixture(LongStream.rangeClosed(1, 60)
                .mapToObj(id -> post(id, 20, Instant.parse("2026-07-09T12:00:00Z")
                        .minus(id, ChronoUnit.MINUTES)))
                .toList());

        defaulted.service.getFeed(VIEWER, TOKEN, null, 0, null).await().indefinitely();
        FeedPage capped = oversized.service.getFeed(VIEWER, TOKEN, null, 500, null)
                .await().indefinitely();

        assertEquals(FeedServiceImpl.DEFAULT_PAGE_SIZE + 1, defaulted.postService.lastRequest.limit());
        assertEquals(FeedServiceImpl.MAX_PAGE_SIZE + 1, oversized.postService.lastRequest.limit());
        assertEquals(FeedServiceImpl.MAX_PAGE_SIZE, capped.posts().size());
    }

    @Test
    void passesViewerAndFollowSetIntoTheRanker() {
        FeedFixture fixture = fixture(List.of(
                post(1, 20, "2026-07-09T12:00:00Z"),
                post(2, 10, "2026-07-09T11:00:00Z")));
        CapturingRanker ranker = new CapturingRanker(List.of(1L, 2L));
        fixture.service.ranker = ranker;

        fixture.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();

        assertEquals(99L, ranker.context.userId());
        assertEquals(Set.of(10L), ranker.context.followedAuthorIds());
        assertEquals(List.of(1L, 2L), ranker.candidates.stream().map(RankablePost::postId).toList());
        assertEquals(VIEWER, fixture.userClient.email);
    }

    @Test
    void aMissingFollowSetFallsBackToNoFollowsRatherThanFailing() {
        FeedFixture nullSet = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));
        FeedFixture nullResponse = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));
        CapturingRanker nullSetRanker = new CapturingRanker(List.of(1L));
        CapturingRanker nullResponseRanker = new CapturingRanker(List.of(1L));
        nullSet.service.ranker = nullSetRanker;
        nullSet.socialClient.following = null;
        nullResponse.service.ranker = nullResponseRanker;
        nullResponse.socialClient.returnNullResponse = true;

        nullSet.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();
        nullResponse.service.getFeed(VIEWER, TOKEN, null, 2, null).await().indefinitely();

        assertEquals(Set.of(), nullSetRanker.context.followedAuthorIds());
        assertEquals(Set.of(), nullResponseRanker.context.followedAuthorIds());
    }

    @Test
    void unknownViewerFailsInsteadOfReturningAnAnonymousFeed() {
        FeedFixture fixture = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));
        fixture.userClient.userId = null;

        PostApiException error = assertThrows(PostApiException.class,
                () -> fixture.service.getFeed("missing@example.com", TOKEN, null, 2, null)
                        .await().indefinitely());

        assertEquals("missing@example.com", fixture.userClient.email);
        assertEquals(401, error.statusCode());
    }

    @Test
    void aMalformedCursorIsRejectedBeforeAnyCandidateIsFetched() {
        FeedFixture fixture = fixture(List.of(post(1, 20, "2026-07-09T12:00:00Z")));

        FeedApiException error = assertThrows(FeedApiException.class,
                () -> fixture.service.getFeed(VIEWER, TOKEN, "!!! not a cursor !!!", 2, null)
                        .await().indefinitely());

        assertEquals(400, error.statusCode());
        assertNull(fixture.postService.lastRequest);
    }

    @Test
    void anEmptyFeedReturnsNoPostsAndNoCursor() {
        FeedFixture fixture = fixture(List.of());

        FeedPage page = fixture.service.getFeed(VIEWER, TOKEN, null, 5, null).await().indefinitely();

        assertEquals(List.of(), page.posts());
        assertNull(page.nextCursor());
    }

    private static PostDto post(long id, long authorId, String createdAt) {
        return post(id, authorId, Instant.parse(createdAt));
    }

    private static PostDto post(long id, long authorId, Instant createdAt) {
        return new PostDto(id, authorId, "Summary " + id, "Should post " + id + " be supported?",
                null, null, false, createdAt, List.of());
    }

    private static PostDto postWithMedia(long id, long authorId, String createdAt,
                                         MediaType... mediaTypes) {
        List<PostMediaDto> media = java.util.stream.IntStream.range(0, mediaTypes.length)
                .mapToObj(index -> new PostMediaDto(
                        mediaTypes[index],
                        Orientation.LANDSCAPE,
                        "posts/" + id + "/" + index,
                        mediaTypes[index] == MediaType.VIDEO ? "video/mp4" : "image/jpeg",
                        null,
                        "https://media.local/" + id + "/" + index,
                        null))
                .toList();
        return new PostDto(id, authorId, "Summary " + id, "Should post " + id + " be supported?",
                null, null, false, Instant.parse(createdAt), media);
    }

    private static FeedFixture fixture(List<PostDto> sourcePosts) {
        FeedServiceImpl service = new FeedServiceImpl();
        KeysetPostService postService = new KeysetPostService(sourcePosts);
        CapturingUserClient userClient = new CapturingUserClient(99L);
        CapturingSocialClient socialClient = new CapturingSocialClient(Set.of(10L));
        service.postService = postService;
        service.ranker = new ChronologicalFollowBoostRanker();
        service.userClient = userClient;
        service.socialClient = socialClient;
        return new FeedFixture(service, postService, userClient, socialClient);
    }

    private record FeedFixture(
            FeedServiceImpl service,
            KeysetPostService postService,
            CapturingUserClient userClient,
            CapturingSocialClient socialClient) {
    }

    private static final class CapturingRanker implements FeedRanker {
        private final List<Long> rankedIds;
        private FeedContext context;
        private List<RankablePost> candidates;

        private CapturingRanker(List<Long> rankedIds) {
            this.rankedIds = rankedIds;
        }

        @Override
        public List<Long> rank(FeedContext context, List<RankablePost> candidates) {
            this.context = context;
            this.candidates = candidates;
            return rankedIds;
        }
    }

    /**
     * A candidate source that really implements keyset semantics — filter, then everything strictly
     * after {@code (createdAt, id)} in {@code createdAt desc, id desc} order, then limit. A fake that
     * just returned the first N posts would let a broken cursor pass.
     */
    private static final class KeysetPostService implements PostService {
        private final List<PostDto> posts;
        private PostPageRequest lastRequest;

        private KeysetPostService(List<PostDto> posts) {
            this.posts = new java.util.ArrayList<>(posts);
        }

        private void publish(PostDto post) {
            posts.add(post);
        }

        @Override
        public Uni<PresignResponse> presignUpload(String authorEmail, String authorization,
                                                  PresignRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<PostDto> getById(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<List<PostDto>> getByUser(Long userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<List<PostDto>> getRecent(int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Uni<List<PostDto>> findPage(PostPageRequest request) {
            lastRequest = request;
            Comparator<PostDto> newestFirst = Comparator
                    .comparing(PostDto::createdAt, Comparator.reverseOrder())
                    .thenComparing(PostDto::id, Comparator.reverseOrder());
            return Uni.createFrom().item(posts.stream()
                    .filter(post -> matchesFilter(post, request.mediaFilter()))
                    .filter(post -> isAfterCursor(post, request))
                    .sorted(newestFirst)
                    .limit(request.limit())
                    .toList());
        }

        private static boolean matchesFilter(PostDto post, PostMediaFilter filter) {
            boolean hasVideo = post.media() != null && post.media().stream()
                    .anyMatch(media -> media.mediaType() == MediaType.VIDEO);
            return switch (filter) {
                case ANY -> true;
                case WITH_VIDEO -> hasVideo;
                case WITHOUT_VIDEO -> !hasVideo;
            };
        }

        private static boolean isAfterCursor(PostDto post, PostPageRequest request) {
            if (request.cursorCreatedAt() == null) {
                return true;
            }
            int byTime = post.createdAt().compareTo(request.cursorCreatedAt());
            return byTime < 0 || (byTime == 0 && post.id() < request.cursorId());
        }
    }

    private static final class CapturingUserClient extends FeedUserClient {
        private Long userId;
        private String email;

        private CapturingUserClient(Long userId) {
            this.userId = userId;
        }

        @Override
        public Uni<FeedUserClient.UserRef> getUserByEmail(String email, String authorization) {
            this.email = email;
            return userId == null
                    ? Uni.createFrom().nullItem()
                    : Uni.createFrom().item(new FeedUserClient.UserRef(userId));
        }
    }

    private static final class CapturingSocialClient extends SocialClient {
        private Set<Long> following;
        private boolean returnNullResponse;

        private CapturingSocialClient(Set<Long> following) {
            this.following = following;
        }

        @Override
        public Uni<SocialClient.FollowingRef> getFollowing(String authorization) {
            return returnNullResponse
                    ? Uni.createFrom().nullItem()
                    : Uni.createFrom().item(new SocialClient.FollowingRef(following));
        }
    }
}
