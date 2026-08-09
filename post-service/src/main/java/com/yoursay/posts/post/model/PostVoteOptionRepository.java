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

    /**
     * Options for a whole page of posts in one query. Mapping a list of posts used to issue one
     * query per post, so a page cost N+1 round trips; callers now fetch once and group by post id.
     * The post is fetch-joined because the caller reads {@code option.getPost().getId()} to group —
     * the posts are already managed at that point, so this adds no extra round trip.
     */
    public Uni<List<PostVoteOption>> listByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return find("select o from PostVoteOption o join fetch o.post p "
                        + "where p.id in :postIds order by p.id, o.ordinal",
                Parameters.with("postIds", postIds)).list();
    }
}
