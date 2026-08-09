package com.yoursay.posts.model;

import com.yoursay.posts.MediaType;
import com.yoursay.posts.PostMediaFilter;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PostRepository implements PanacheRepository<Post> {

    private static final String NEWEST_FIRST = " order by p.createdAt desc, p.id desc";

    /** True when the post carries at least one video — the SQL form of the feed's type filter. */
    private static final String HAS_VIDEO =
            "exists (select 1 from PostMedia m where m.post = p and m.mediaType = :videoType)";

    /**
     * True when the post carries the selected topic — the SQL form of the category feed's filter
     * (ADR-043), served by {@code idx_post_topic_topic}.
     *
     * <p>{@code PostTopic} belongs to the topics domain and is named here only as an HQL entity, not
     * imported: the join is by post id, matching how this domain already references users.
     */
    private static final String HAS_TOPIC =
            "exists (select 1 from PostTopic pt where pt.postId = p.id and pt.topicId = :topicId)";

    public Uni<Post> savePost(Post post) {
        return persist(post).replaceWith(post);
    }

    /** Fetch a single post with its media eagerly loaded so it can be mapped outside the session. */
    public Uni<Post> getPostById(Long id) {
        return find("select distinct p from Post p left join fetch p.media where p.id = :id",
                Parameters.with("id", id)).firstResult();
    }

    public Uni<List<Post>> getPostsByUser(Long userId) {
        return find("select distinct p from Post p left join fetch p.media "
                        + "where p.userId = :userId order by p.createdAt desc, p.id desc",
                Parameters.with("userId", userId)).list();
    }

    /**
     * A page of recent posts, newest first, addressed by offset. Retained for {@code GET /posts} and
     * the Unwrapped sweep; the feed uses {@link #findPageAfter} instead.
     */
    public Uni<List<Post>> getRecent(int page, int size) {
        return find("from Post p" + NEWEST_FIRST).page(page, size).list()
                .flatMap(this::fetchWithMedia);
    }

    /**
     * A keyset page: the newest {@code limit} posts that come strictly after
     * {@code (cursorCreatedAt, cursorId)} in {@code createdAt desc, id desc} order, optionally
     * restricted by the media they carry. Both cursor arguments are null for the first page.
     *
     * <p>Ordering by the same {@code (created_at, id)} tuple the cursor names is what makes paging
     * total and gap-free — {@code idx_post_created_at_id} serves both the sort and the predicate.
     * The media filter is a SQL {@code exists} rather than a post-fetch filter, so a page is full
     * whenever matching posts remain and the reader never sees a false end of feed.
     */
    public Uni<List<Post>> findPageAfter(Instant cursorCreatedAt, Long cursorId,
                                         PostMediaFilter mediaFilter, String topicId, int limit) {
        List<String> conditions = new ArrayList<>();
        Parameters parameters = new Parameters();

        if (cursorCreatedAt != null && cursorId != null) {
            conditions.add("(p.createdAt < :cursorCreatedAt"
                    + " or (p.createdAt = :cursorCreatedAt and p.id < :cursorId))");
            parameters.and("cursorCreatedAt", cursorCreatedAt).and("cursorId", cursorId);
        }
        if (mediaFilter == PostMediaFilter.WITH_VIDEO || mediaFilter == PostMediaFilter.WITHOUT_VIDEO) {
            conditions.add(mediaFilter == PostMediaFilter.WITH_VIDEO ? HAS_VIDEO : "not " + HAS_VIDEO);
            parameters.and("videoType", MediaType.VIDEO);
        }
        if (topicId != null && !topicId.isBlank()) {
            conditions.add(HAS_TOPIC);
            parameters.and("topicId", topicId);
        }

        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return find("from Post p" + where + NEWEST_FIRST, parameters).page(0, limit).list()
                .flatMap(this::fetchWithMedia);
    }

    /**
     * A collection fetch-join can't be paged or limited in SQL (Hibernate would page in memory over
     * the joined rows), so both paging methods select the post rows first — a clean, indexed query
     * with no join — then fetch exactly those posts with their media.
     */
    private Uni<List<Post>> fetchWithMedia(List<Post> pagePosts) {
        if (pagePosts.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        List<Long> ids = pagePosts.stream().map(Post::getId).toList();
        return find("select distinct p from Post p left join fetch p.media "
                        + "where p.id in :ids" + NEWEST_FIRST,
                Parameters.with("ids", ids)).list();
    }
}
