package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostSourceRepository implements PanacheRepository<PostSource> {

    public Uni<List<PostSource>> listByPostIds(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return find("post.id in ?1 order by post.id, ordinal", postIds).list();
    }
}
