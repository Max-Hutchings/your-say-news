package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostRepository implements PanacheRepository<Post> {

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
     * A page of recent posts, newest first, with media eagerly loaded. A collection fetch-join
     * can't be paged in SQL (Hibernate would page in memory over the joined rows), so we page the
     * post ids first — a clean, indexed query with no join — then fetch that page's posts with
     * their media.
     */
    public Uni<List<Post>> getRecent(int page, int size) {
        return find("from Post p order by p.createdAt desc, p.id desc")
                .page(page, size).list()
                .flatMap(pagePosts -> {
                    if (pagePosts.isEmpty()) {
                        return Uni.createFrom().item(List.<Post>of());
                    }
                    List<Long> ids = pagePosts.stream().map(Post::getId).toList();
                    return find("select distinct p from Post p left join fetch p.media "
                                    + "where p.id in :ids order by p.createdAt desc, p.id desc",
                            Parameters.with("ids", ids)).list();
                });
    }
}
