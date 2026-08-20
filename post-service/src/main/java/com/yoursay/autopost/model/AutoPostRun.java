package com.yoursay.autopost.model;

import com.yoursay.autopost.AutoPostRunStatus;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_post_run")
public class AutoPostRun extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "triggered_by_admin_id", nullable = false)
    private Long triggeredByAdminId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AutoPostRunStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "selected_candidate_id")
    private UUID selectedCandidateId;

    @Column(name = "pepper_draft_id")
    private UUID pepperDraftId;

    @Column(name = "published_post_id")
    private Long publishedPostId;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @Column(name = "provider_response_id", length = 160)
    private String providerResponseId;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AutoPostRun() {
    }

    public AutoPostRun(Long triggeredByAdminId, Instant windowStart, Instant windowEnd,
                       String promptVersion) {
        this.id = UUID.randomUUID();
        this.triggeredByAdminId = triggeredByAdminId;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.promptVersion = promptVersion;
        this.status = AutoPostRunStatus.QUEUED;
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

    public void markDiscovering() {
        status = AutoPostRunStatus.DISCOVERING;
        attemptCount++;
        nextAttemptAt = null;
        errorCode = null;
        errorMessage = null;
    }

    public void markCandidatesReady(String model, String providerResponseId) {
        status = AutoPostRunStatus.CANDIDATES_READY;
        this.model = bounded(model, 128);
        this.providerResponseId = bounded(providerResponseId, 160);
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
        this.completedAt = Instant.now();
    }

    public void markRetry(String code, Instant retryAt) {
        status = AutoPostRunStatus.QUEUED;
        errorCode = bounded(code, 80);
        errorMessage = "Story discovery will be retried.";
        nextAttemptAt = retryAt;
    }

    public void markFailed(String code, String message) {
        status = AutoPostRunStatus.FAILED;
        errorCode = bounded(code, 80);
        errorMessage = bounded(message, 512);
        nextAttemptAt = null;
        completedAt = Instant.now();
    }

    public void markDrafting(UUID candidateId, UUID draftId) {
        if (selectedCandidateId != null && !selectedCandidateId.equals(candidateId)) {
            throw new IllegalStateException("A different candidate is already selected");
        }
        selectedCandidateId = candidateId;
        pepperDraftId = draftId;
        status = AutoPostRunStatus.DRAFTING;
        completedAt = null;
    }

    public void markDraftReady() {
        status = AutoPostRunStatus.DRAFT_READY;
        errorCode = null;
        errorMessage = null;
    }

    public void markDraftFailed() {
        markFailed("AUTO_POST_DRAFT_FAILED", "Post agent could not create the draft. Try a new run.");
    }

    public void markPublishing() {
        status = AutoPostRunStatus.PUBLISHING;
        errorCode = null;
        errorMessage = null;
    }

    public void markPublicationReadyForRetry(String code, String message) {
        status = AutoPostRunStatus.DRAFT_READY;
        errorCode = bounded(code, 80);
        errorMessage = bounded(message, 512);
    }

    public void markPublished(Long postId) {
        if (publishedPostId != null && !publishedPostId.equals(postId)) {
            throw new IllegalStateException("Auto-post run is already linked to another post");
        }
        publishedPostId = postId;
        status = AutoPostRunStatus.PUBLISHED;
        errorCode = null;
        errorMessage = null;
        completedAt = Instant.now();
    }

    private static String bounded(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() { return id; }
    public Long getTriggeredByAdminId() { return triggeredByAdminId; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public AutoPostRunStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public UUID getSelectedCandidateId() { return selectedCandidateId; }
    public UUID getPepperDraftId() { return pepperDraftId; }
    public Long getPublishedPostId() { return publishedPostId; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
