package com.yoursay.topics.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Durable provenance for one claim that a topic tag applies to a post. */
@Entity
@Table(name = "post_topic_tag_assignment")
public class PostTopicTagAssignment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "topic_tag_id", nullable = false, length = 64)
    private String topicTagId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private TopicTagAssignmentSource source;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "classifier_version", length = 128)
    private String classifierVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false, length = 16)
    private TopicTagReviewState reviewState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostTopicTagAssignment() {
    }

    private PostTopicTagAssignment(Long postId, String topicTagId,
                                   TopicTagAssignmentSource source,
                                   TopicTagReviewState reviewState) {
        this.postId = postId;
        this.topicTagId = topicTagId;
        this.source = source;
        this.reviewState = reviewState;
    }

    public static PostTopicTagAssignment creator(Long postId, String topicTagId) {
        return new PostTopicTagAssignment(postId, topicTagId,
                TopicTagAssignmentSource.CREATOR, TopicTagReviewState.ACCEPTED);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getTopicTagId() {
        return topicTagId;
    }

    public TopicTagAssignmentSource getSource() {
        return source;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getClassifierVersion() {
        return classifierVersion;
    }

    public TopicTagReviewState getReviewState() {
        return reviewState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
