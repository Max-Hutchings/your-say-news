package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Search projection used for public chips and category feed membership. */
@Entity
@Table(name = "effective_post_topic_tag")
@IdClass(EffectivePostTopicTagId.class)
public class EffectivePostTopicTag extends PanacheEntityBase {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "topic_tag_id", length = 64)
    private String topicTagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_tag_id", insertable = false, updatable = false)
    private Topic topicTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "effective_source", nullable = false, length = 16)
    private TopicTagAssignmentSource effectiveSource;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EffectivePostTopicTag() {
    }

    public EffectivePostTopicTag(Long postId, String topicTagId,
                                 TopicTagAssignmentSource effectiveSource) {
        this.postId = postId;
        this.topicTagId = topicTagId;
        this.effectiveSource = effectiveSource;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    public Long getPostId() {
        return postId;
    }

    public String getTopicTagId() {
        return topicTagId;
    }

    public Topic getTopicTag() {
        return topicTag;
    }

    public TopicTagAssignmentSource getEffectiveSource() {
        return effectiveSource;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
