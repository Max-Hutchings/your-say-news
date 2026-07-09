package com.yoursay.feed.service;

import com.yoursay.feed.client.FeedUserClient;
import com.yoursay.feed.client.SocialClient;
import com.yoursay.feed.FeedContext;
import com.yoursay.feed.FeedRanker;
import com.yoursay.feed.RankablePost;
import com.yoursay.posts.*;
import com.yoursay.posts.error.PostApiException;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedServiceImplTest {

    @Test
    void assemblesFeedWithFollowBoostBeforePaging() {
        FeedServiceImpl service = new FeedServiceImpl();
        FakePostService posts = new FakePostService(List.of(
                post(1, 20, "2026-07-09T11:00:00Z"),
                post(2, 10, "2026-07-09T10:00:00Z"),
                post(3, 30, "2026-07-09T09:00:00Z"),
                post(4, 10, "2026-07-09T08:00:00Z")
        ));
        CapturingUserClient userClient = new CapturingUserClient(99L);
        CapturingSocialClient socialClient = new CapturingSocialClient(Set.of(10L));
        service.postService = posts;
        service.ranker = new ChronologicalFollowBoostRanker();
        service.userClient = userClient;
        service.socialClient = socialClient;

        List<PostDto> firstPage = service.getFeed("viewer@example.com", "Bearer token", 0, 2)
                .await().indefinitely();
        List<PostDto> secondPage = service.getFeed("viewer@example.com", "Bearer token", 1, 2)
                .await().indefinitely();

        assertEquals(List.of(2L, 4L), firstPage.stream().map(PostDto::id).toList());
        assertEquals(List.of(1L, 3L), secondPage.stream().map(PostDto::id).toList());
        assertEquals(0, posts.lastPage);
        assertEquals(FeedServiceImpl.MAX_RANKING_WINDOW, posts.lastSize);
        assertEquals("viewer@example.com", userClient.email);
        assertEquals("Bearer token", userClient.authorization);
        assertEquals("Bearer token", socialClient.authorization);
    }

    @Test
    void passesViewerAndFollowSetIntoRanker() {
        FeedFixture fixture = baseService(List.of(
                post(1, 20, "2026-07-09T11:00:00Z"),
                post(2, 10, "2026-07-09T10:00:00Z")
        ));
        CapturingRanker ranker = new CapturingRanker(List.of(1L, 2L));
        fixture.service.ranker = ranker;

        fixture.service.getFeed("viewer@example.com", "Bearer token", 0, 2).await().indefinitely();

        assertEquals(99L, ranker.context.userId());
        assertEquals(Set.of(10L), ranker.context.followedAuthorIds());
        assertEquals(List.of(1L, 2L), ranker.candidates.stream().map(RankablePost::postId).toList());
        assertEquals("viewer@example.com", fixture.userClient.email);
        assertEquals("Bearer token", fixture.userClient.authorization);
        assertEquals("Bearer token", fixture.socialClient.authorization);
    }

    @Test
    void normalizesPagingAndHandlesMissingFollowSet() {
        FeedFixture fixture = baseService(List.of(
                post(1, 20, "2026-07-09T11:00:00Z"),
                post(2, 10, "2026-07-09T10:00:00Z")
        ));
        fixture.socialClient.following = null;

        List<PostDto> negativePage = fixture.service.getFeed("viewer@example.com", "Bearer token", -3, 0)
                .await().indefinitely();

        assertEquals(List.of(1L, 2L), negativePage.stream().map(PostDto::id).toList());
        assertEquals(0, fixture.postService.lastPage);
        assertEquals(FeedServiceImpl.MAX_RANKING_WINDOW, fixture.postService.lastSize);
        assertEquals("Bearer token", fixture.socialClient.authorization);
    }

    @Test
    void capsOversizedPageRequestsToMaxPageSize() {
        FeedFixture fixture = baseService(LongStream.rangeClosed(1, 60)
                .mapToObj(id -> post(id, 20, Instant.parse("2026-07-09T12:00:00Z")
                        .minus(id, ChronoUnit.MINUTES).toString()))
                .toList());

        List<PostDto> page = fixture.service.getFeed("viewer@example.com", "Bearer token", 0, 500)
                .await().indefinitely();

        assertEquals(50, page.size());
        assertEquals(LongStream.rangeClosed(1, 50).boxed().toList(),
                page.stream().map(PostDto::id).toList());
        assertEquals(FeedServiceImpl.MAX_RANKING_WINDOW, fixture.postService.lastSize);
    }

    @Test
    void nullFollowingResponseFallsBackToEmptyFollowSet() {
        FeedFixture fixture = baseService(List.of(
                post(1, 20, "2026-07-09T11:00:00Z"),
                post(2, 10, "2026-07-09T10:00:00Z")
        ));
        CapturingRanker ranker = new CapturingRanker(List.of(1L, 2L));
        fixture.service.ranker = ranker;
        fixture.socialClient.returnNullResponse = true;

        fixture.service.getFeed("viewer@example.com", "Bearer token", 0, 2).await().indefinitely();

        assertEquals(Set.of(), ranker.context.followedAuthorIds());
        assertEquals("Bearer token", fixture.socialClient.authorization);
    }

    @Test
    void unknownViewerFailsInsteadOfReturningAnonymousFeed() {
        FeedFixture fixture = baseService(List.of(
                post(1, 20, "2026-07-09T11:00:00Z")
        ));
        fixture.userClient.userId = null;

        PostApiException error = assertThrows(PostApiException.class,
                () -> fixture.service.getFeed("missing@example.com", "Bearer token", 0, 2)
                        .await().indefinitely());

        assertEquals("missing@example.com", fixture.userClient.email);
        assertEquals("Bearer token", fixture.userClient.authorization);
        assertEquals("Cannot create post because author lookup failed: authorEmail=missing@example.com",
                error.getMessage());
    }

    private static PostDto post(long id, long authorId, String createdAt) {
        return new PostDto(
                id,
                authorId,
                "Post " + id,
                "Summary " + id,
                "Do you agree?",
                null,
                null,
                false,
                Instant.parse(createdAt),
                List.of());
    }

    private static FeedFixture baseService(List<PostDto> sourcePosts) {
        FeedServiceImpl service = new FeedServiceImpl();
        FakePostService postService = new FakePostService(sourcePosts);
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
            FakePostService postService,
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

    private static final class FakePostService implements PostService {
        private final List<PostDto> posts;
        private int lastPage = -1;
        private int lastSize = -1;

        private FakePostService(List<PostDto> posts) {
            this.posts = posts;
        }

        @Override
        public Uni<PresignResponse> presignUpload(String authorEmail, String authorization, PresignRequest request) {
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
            lastPage = page;
            lastSize = size;
            return Uni.createFrom().item(posts.stream().limit(size).toList());
        }
    }

    private static final class CapturingUserClient implements FeedUserClient {
        private Long userId;
        private String email;
        private String authorization;

        private CapturingUserClient(Long userId) {
            this.userId = userId;
        }

        @Override
        public Uni<UserRef> getUserByEmail(String email, String authorization) {
            this.email = email;
            this.authorization = authorization;
            return userId == null
                    ? Uni.createFrom().nullItem()
                    : Uni.createFrom().item(new UserRef(userId));
        }
    }

    private static final class CapturingSocialClient implements SocialClient {
        private Set<Long> following;
        private boolean returnNullResponse;
        private String authorization;

        private CapturingSocialClient(Set<Long> following) {
            this.following = following;
        }

        @Override
        public Uni<FollowingRef> getFollowing(String authorization) {
            this.authorization = authorization;
            return returnNullResponse
                    ? Uni.createFrom().nullItem()
                    : Uni.createFrom().item(new FollowingRef(following));
        }
    }
}
