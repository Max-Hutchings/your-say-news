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

    public Uni<List<Post>> getRecent(int limit) {
        return find("select distinct p from Post p left join fetch p.media order by p.createdAt desc, p.id desc")
                .page(0, limit).list();
    }
}
