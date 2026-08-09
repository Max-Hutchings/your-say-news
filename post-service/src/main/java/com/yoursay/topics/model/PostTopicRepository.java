package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class PostTopicRepository implements PanacheRepositoryBase<PostTopic, PostTopicId> {

    /**
     * Every assignment for a page of posts, with its catalogue row fetch-joined so labels are
     * available outside the session. One query per page, never one per post — the N+1 that ADR-042
     * removed from vote options must not come back here.
     */
    public Uni<List<PostTopic>> listByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return find("select pt from PostTopic pt join fetch pt.topic t "
                        + "where pt.postId in :postIds order by t.displayOrder asc, t.id asc",
                Parameters.with("postIds", postIds)).list();
    }

    public Uni<Void> assign(Long postId, List<String> topicIds) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String topicId : topicIds) {
            chain = chain.chain(() -> persist(new PostTopic(postId, topicId)).replaceWithVoid());
        }
        return chain;
    }
}
