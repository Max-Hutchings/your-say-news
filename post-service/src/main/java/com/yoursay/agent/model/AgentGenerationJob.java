package com.yoursay.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.agent.AgentJobStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_generation_job")
public class AgentGenerationJob extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request", nullable = false, columnDefinition = "text")
    private String request;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_response_id", length = 128)
    private String providerResponseId;

    @Column(name = "draft", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode draft;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "published_post_id")
    private Long publishedPostId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AgentGenerationJob() {
    }

    public AgentGenerationJob(Long userId, String request) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.request = request;
        this.status = AgentJobStatus.PENDING;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markResearching() {
        status = AgentJobStatus.RESEARCHING;
        attemptCount++;
        startedAt = Instant.now();
        nextAttemptAt = null;
        errorCode = null;
        errorMessage = null;
    }

    public void markDraftReady(JsonNode draft, String model, String providerResponseId) {
        this.status = AgentJobStatus.DRAFT_READY;
        this.draft = draft;
        this.model = model;
        this.providerResponseId = providerResponseId;
        this.completedAt = Instant.now();
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markRetry(String errorCode, String errorMessage, Instant nextAttemptAt) {
        this.status = AgentJobStatus.PENDING;
        this.errorCode = errorCode;
        this.errorMessage = bounded(errorMessage);
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.status = AgentJobStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = bounded(errorMessage);
        this.completedAt = Instant.now();
        this.nextAttemptAt = null;
    }

    private static String bounded(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRequest() {
        return request;
    }

    public AgentJobStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getModel() {
        return model;
    }

    public JsonNode getDraft() {
        return draft;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getPublishedPostId() {
        return publishedPostId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
