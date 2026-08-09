package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A topic assigned to a post — at most three per post (ADR-043).
 *
 * <p>{@code postId} is a plain column rather than an association, deliberately: {@code Post} lives
 * in another domain's internal {@code model} package, and the codebase already crosses that boundary
 * by id (as {@code Post.userId} does for users) rather than by entity reference. The topic side
 * <em>is</em> an association, because both entities belong to this domain, and it lets a page of
 * posts be decorated with labels in one fetch-join.
 */
@Entity
@Table(name = "post_topic")
@IdClass(PostTopicId.class)
public class PostTopic extends PanacheEntityBase {

    @Id
    @Column(name = "post_id")
    private Long postId;

    @Id
    @Column(name = "topic_id", length = 64)
    private String topicId;

    /** Read-only view of the catalogue row; {@link #topicId} owns the column. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", insertable = false, updatable = false)
    private Topic topic;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostTopic() {
    }

    public PostTopic(Long postId, String topicId) {
        this.postId = postId;
        this.topicId = topicId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getPostId() {
        return postId;
    }

    public String getTopicId() {
        return topicId;
    }

    public Topic getTopic() {
        return topic;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
