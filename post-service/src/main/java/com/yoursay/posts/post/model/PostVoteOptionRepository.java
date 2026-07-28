package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class PostVoteOptionRepository implements PanacheRepository<PostVoteOption> {
    public Uni<List<PostVoteOption>> listByPostId(Long postId) {
        return find("post.id = :postId order by ordinal", Parameters.with("postId", postId)).list();
    }
}
