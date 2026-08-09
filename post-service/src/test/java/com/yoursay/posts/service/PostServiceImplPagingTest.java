package com.yoursay.posts.service;

import com.yoursay.posts.PostMediaFilter;
import com.yoursay.posts.dto.PostPageRequest;
import com.yoursay.posts.model.Post;
import com.yoursay.posts.model.PostRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The normalisation {@code PostServiceImpl.findPage} applies before it reaches the database. The
 * limit ceiling is the subtle one: the feed asks for {@code pageSize + 1} to probe for another row,
 * so clamping to {@code MAX_PAGE_SIZE} rather than {@code MAX_PAGE_SIZE + 1} would swallow the probe
 * on a full-size request and report a permanent false end of feed at exactly {@code size=50}.
 */
class PostServiceImplPagingTest {

    @Test
    void allowsTheFeedsEndOfFeedProbeOnAMaximumSizedPage() {
        CapturingPostRepository repository = new CapturingPostRepository();
        PostServiceImpl service = serviceWith(repository);

        service.findPage(new PostPageRequest(null, null, PostMediaFilter.ANY, null,
                PostServiceImpl.MAX_PAGE_SIZE + 1)).await().indefinitely();

        assertEquals(PostServiceImpl.MAX_PAGE_SIZE + 1, repository.limit);
    }

    @Test
    void capsALimitBeyondTheProbeAndDefaultsAnAbsentOne() {
        CapturingPostRepository oversized = new CapturingPostRepository();
        CapturingPostRepository absent = new CapturingPostRepository();

        serviceWith(oversized).findPage(new PostPageRequest(null, null, PostMediaFilter.ANY, null, 5000))
                .await().indefinitely();
        serviceWith(absent).findPage(new PostPageRequest(null, null, PostMediaFilter.ANY, null, 0))
                .await().indefinitely();

        assertEquals(PostServiceImpl.MAX_PAGE_SIZE + 1, oversized.limit);
        assertEquals(PostServiceImpl.DEFAULT_PAGE_SIZE, absent.limit);
    }

    @Test
    void passesTheCursorAndTopicThroughAndDefaultsAnAbsentMediaFilter() {
        CapturingPostRepository repository = new CapturingPostRepository();
        Instant cursorCreatedAt = Instant.parse("2026-07-09T11:22:33.123456789Z");

        serviceWith(repository)
                .findPage(new PostPageRequest(cursorCreatedAt, 4242L, null, "housing", 5))
                .await().indefinitely();

        assertEquals(cursorCreatedAt, repository.cursorCreatedAt);
        assertEquals(4242L, repository.cursorId);
        assertEquals(PostMediaFilter.ANY, repository.mediaFilter);
        // The topic reaches the query unmodified: normalisation is the feed's job, and a service
        // that quietly dropped it would turn a category feed into the full feed.
        assertEquals("housing", repository.topicId);
    }

    @Test
    void anEmptyPageSkipsTheVoteOptionQueryEntirely() {
        CapturingPostRepository repository = new CapturingPostRepository();
        PostServiceImpl service = serviceWith(repository);

        List<?> page = service.findPage(PostPageRequest.first(5)).await().indefinitely();

        assertEquals(List.of(), page);
        // A null option repository would NPE if the empty page still tried to fetch options.
        assertNull(service.optionRepository);
    }

    private static PostServiceImpl serviceWith(PostRepository repository) {
        PostServiceImpl service = new PostServiceImpl();
        service.postRepository = repository;
        return service;
    }

    /** Records what the service asked the database for; always answers with an empty page. */
    private static final class CapturingPostRepository extends PostRepository {
        private Instant cursorCreatedAt;
        private Long cursorId;
        private PostMediaFilter mediaFilter;
        private String topicId;
        private int limit = -1;

        @Override
        public Uni<List<Post>> findPageAfter(Instant cursorCreatedAt, Long cursorId,
                                             PostMediaFilter mediaFilter, String topicId, int limit) {
            this.cursorCreatedAt = cursorCreatedAt;
            this.cursorId = cursorId;
            this.mediaFilter = mediaFilter;
            this.topicId = topicId;
            this.limit = limit;
            return Uni.createFrom().item(List.of());
        }
    }
}
