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
@Table(name = "unwrapped_analysis_job")
public class UnwrappedAnalysisJob extends PanacheEntityBase {
    @Id
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UnwrappedMode mode;

    @Column(name = "post_id", nullable = false)
    Long postId;

    Integer milestone;

    @Column(name = "analysis_version", nullable = false)
    String analysisVersion;

    @Column(name = "prediction_version")
    String predictionVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UnwrappedJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    int attemptCount;

    @Column(name = "next_attempt_at")
    Instant nextAttemptAt;

    @Column(name = "canonical_vote_count")
    Long canonicalVoteCount;

    @Column(name = "aggregate_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    JsonNode aggregateJson;

    @Column(name = "aggregate_version")
    String aggregateVersion;

    String model;

    @Column(name = "provider_response_id")
    String providerResponseId;

    @Column(name = "error_code")
    String errorCode;

    @Column(name = "error_message")
    String errorMessage;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "started_at")
    Instant startedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    protected UnwrappedAnalysisJob() {
    }

    public UnwrappedAnalysisJob(UnwrappedMode mode, Long postId, Integer milestone,
                                String analysisVersion, String predictionVersion) {
        this.id = UUID.randomUUID();
        this.mode = mode;
        this.postId = postId;
        this.milestone = milestone;
        this.analysisVersion = analysisVersion;
        this.predictionVersion = predictionVersion;
        this.status = UnwrappedJobStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UnwrappedMode getMode() { return mode; }
    public Long getPostId() { return postId; }
    public Integer getMilestone() { return milestone; }
    public String getAnalysisVersion() { return analysisVersion; }
    public UnwrappedJobStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Long getCanonicalVoteCount() { return canonicalVoteCount; }
    public JsonNode getAggregateJson() { return aggregateJson; }
    public String getAggregateVersion() { return aggregateVersion; }

    public void claim() {
        status = UnwrappedJobStatus.GENERATING;
        attemptCount++;
        startedAt = Instant.now();
        errorCode = null;
        errorMessage = null;
    }

    public void attachAggregate(long count, String version, JsonNode json) {
        canonicalVoteCount = count;
        aggregateVersion = version;
        aggregateJson = json;
    }

    public void complete(String model, String providerResponseId) {
        this.model = model;
        this.providerResponseId = providerResponseId;
        this.status = UnwrappedJobStatus.DRAFT_READY;
        this.completedAt = Instant.now();
    }

    public void fail(String code, String message, boolean retry) {
        errorCode = code;
        errorMessage = message == null ? null : message.substring(0, Math.min(512, message.length()));
        if (retry && attemptCount < 3) {
            status = UnwrappedJobStatus.PENDING;
            nextAttemptAt = Instant.now().plusSeconds(30L * attemptCount);
        } else {
            status = UnwrappedJobStatus.FAILED;
            completedAt = Instant.now();
        }
    }

    public void recoverStaleClaim() {
        fail("UNWRAPPED_CLAIM_EXPIRED",
                "A generation worker stopped before completing this job.", true);
    }
}
