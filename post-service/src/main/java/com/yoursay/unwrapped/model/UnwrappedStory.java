package com.yoursay.unwrapped.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.unwrapped.UnwrappedMode;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "unwrapped_story")
public class UnwrappedStory extends PanacheEntityBase {
    @Id
    UUID id;
    @Column(name = "job_id", nullable = false)
    UUID jobId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UnwrappedMode mode;
    @Column(name = "post_id", nullable = false)
    Long postId;
    Integer milestone;
    @Column(name = "canonical_vote_count", nullable = false)
    long canonicalVoteCount;
    @Column(name = "aggregate_version")
    String aggregateVersion;
    @Column(name = "story_schema_version", nullable = false)
    String storySchemaVersion;
    @Column(name = "analysis_version", nullable = false)
    String analysisVersion;
    @Column(name = "prompt_version", nullable = false)
    String promptVersion;
    @Column(name = "rule_set_version", nullable = false)
    String ruleSetVersion;
    @Column(nullable = false)
    String model;
    @Column(name = "story_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    JsonNode storyJson;
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    UnwrappedReviewStatus reviewStatus;
    @Column(name = "reviewer_user_id")
    Long reviewerUserId;
    @Column(name = "review_note")
    String reviewNote;
    @Column(name = "generated_at", nullable = false)
    Instant generatedAt;
    @Column(name = "reviewed_at")
    Instant reviewedAt;

    protected UnwrappedStory() {
    }

    public UnwrappedStory(UnwrappedAnalysisJob job, JsonNode storyJson, String model) {
        this.id = UUID.randomUUID();
        this.jobId = job.getId();
        this.mode = job.getMode();
        this.postId = job.getPostId();
        this.milestone = job.getMilestone();
        this.canonicalVoteCount = job.getCanonicalVoteCount() == null ? 0 : job.getCanonicalVoteCount();
        this.aggregateVersion = job.getAggregateVersion();
        this.storySchemaVersion = "unwrapped-story-v1";
        this.analysisVersion = job.getAnalysisVersion();
        this.promptVersion = "unwrapped-case-v1";
        this.ruleSetVersion = "cohort-rules-v1";
        this.model = model;
        this.storyJson = storyJson;
        this.reviewStatus = UnwrappedReviewStatus.DRAFT;
        this.generatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UnwrappedMode getMode() { return mode; }
    public Long getPostId() { return postId; }
    public Integer getMilestone() { return milestone; }
    public long getCanonicalVoteCount() { return canonicalVoteCount; }
    public String getAggregateVersion() { return aggregateVersion; }
    public JsonNode getStoryJson() { return storyJson; }
    public UnwrappedReviewStatus getReviewStatus() { return reviewStatus; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getModel() { return model; }

    public void approve(Long reviewerUserId) {
        this.reviewStatus = UnwrappedReviewStatus.APPROVED;
        this.reviewerUserId = reviewerUserId;
        this.reviewNote = null;
        this.reviewedAt = Instant.now();
    }

    public void reject(Long reviewerUserId, String note) {
        this.reviewStatus = UnwrappedReviewStatus.REJECTED;
        this.reviewerUserId = reviewerUserId;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }
}
