package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class PostTopicTagRepository implements PanacheRepository<PostTopicTagAssignment> {

    public Uni<List<EffectivePostTopicTag>> listEffectiveByPostIds(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return getSession().chain(session -> session.createQuery(
                        "select e from EffectivePostTopicTag e join fetch e.topicTag t "
                                + "where e.postId in :postIds "
                                + "order by t.displayOrder asc, t.id asc",
                        EffectivePostTopicTag.class)
                .setParameter("postIds", postIds)
                .getResultList());
    }

    public Uni<Void> assignCreatorTags(Long postId, List<String> topicTagIds) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (String topicTagId : topicTagIds) {
            chain = chain.chain(() -> persist(PostTopicTagAssignment.creator(postId, topicTagId))
                    .replaceWithVoid())
                    .chain(() -> getSession().chain(session -> session.persist(
                            new EffectivePostTopicTag(postId, topicTagId,
                                    TopicTagAssignmentSource.CREATOR))));
        }
        return chain;
    }
}
