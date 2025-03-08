package com.yoursay.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostRepository implements PanacheRepository<Post> {

    public Uni<Post> savePost(Post post) {
        return post.persist();
    }

    public Uni<Post> getPostById(Long id) {
        return findById(id);
    }

    public Uni<List<Post>> getPostsByUser(Long userId){
        return Post.list("userId", userId);
    }
}
