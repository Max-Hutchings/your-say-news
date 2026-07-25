package com.yoursay.unwrapped.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unwrapped_follow_up")
public class UnwrappedFollowUp extends PanacheEntityBase {
    @Id
    UUID id;
    @Column(name = "user_id", nullable = false)
    Long userId;
    @Column(name = "post_id", nullable = false)
    Long postId;
    @Column(name = "story_id", nullable = false)
    UUID storyId;
    @Column(name = "original_option_id", nullable = false)
    Long originalOptionId;
    @Column(name = "follow_up_option_id", nullable = false)
    Long followUpOptionId;
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected UnwrappedFollowUp() {
    }

    public UnwrappedFollowUp(Long userId, Long postId, UUID storyId,
                             Long originalOptionId, Long followUpOptionId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.postId = postId;
        this.storyId = storyId;
        this.originalOptionId = originalOptionId;
        this.followUpOptionId = followUpOptionId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getPostId() { return postId; }
    public UUID getStoryId() { return storyId; }
    public Long getOriginalOptionId() { return originalOptionId; }
    public Long getFollowUpOptionId() { return followUpOptionId; }
    public Instant getCreatedAt() { return createdAt; }
}
