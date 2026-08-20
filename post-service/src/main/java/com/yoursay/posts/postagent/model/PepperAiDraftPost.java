package com.yoursay.posts.postagent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.posts.postagent.PepperDraftStatus;
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
@Table(name = "pepper_ai_draft_post")
public class PepperAiDraftPost extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "prompt", nullable = false, columnDefinition = "text")
    private String prompt;

    @Column(name = "replica_id", nullable = false, length = 128)
    private String replicaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PepperDraftStatus status;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "generated_content", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode generatedContent;

    @Column(name = "content", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode content;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider_response_id", length = 128)
    private String providerResponseId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 160)
    private String errorMessage;

    @Column(name = "published_post_id")
    private Long publishedPostId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PepperAiDraftPost() {
    }

    public PepperAiDraftPost(Long userId, String prompt, String replicaId) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.prompt = prompt;
        this.replicaId = replicaId;
        this.status = PepperDraftStatus.RECEIVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markGenerating() {
        status = PepperDraftStatus.GENERATING;
        success = null;
        errorCode = null;
        errorMessage = null;
    }

    public void markFinished(JsonNode generatedContent, String model, String providerResponseId) {
        status = PepperDraftStatus.FINISHED;
        success = true;
        this.generatedContent = generatedContent;
        this.content = generatedContent.deepCopy();
        this.model = model;
        this.providerResponseId = providerResponseId;
        this.version = 1;
        this.completedAt = Instant.now();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String safeMessage) {
        status = PepperDraftStatus.FAILED;
        success = false;
        this.errorCode = errorCode;
        this.errorMessage = safeMessage;
        this.completedAt = Instant.now();
    }

    public void replaceContent(JsonNode content) {
        this.content = content;
        version++;
    }

    public void markPublished(Long postId) {
        if (publishedPostId != null && !publishedPostId.equals(postId)) {
            throw new IllegalStateException("Pepper draft is already linked to another post");
        }
        publishedPostId = postId;
    }

    public UUID getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPrompt() { return prompt; }
    public String getReplicaId() { return replicaId; }
    public PepperDraftStatus getStatus() { return status; }
    public Boolean getSuccess() { return success; }
    public JsonNode getGeneratedContent() { return generatedContent; }
    public JsonNode getContent() { return content; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Long getPublishedPostId() { return publishedPostId; }
    public int getVersion() { return version; }
}
