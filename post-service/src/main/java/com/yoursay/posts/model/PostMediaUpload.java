package com.yoursay.posts.model;

import com.yoursay.posts.MediaType;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Server-side reservation for a presigned upload. A post may only attach keys that were minted
 * for the same authenticated user and have not already been consumed.
 */
@Entity
@Table(name = "post_media_upload")
public class PostMediaUpload extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private MediaType mediaType;

    @Column(name = "s3_key", nullable = false, unique = true, length = 1024)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PostMediaUpload() {
    }

    public PostMediaUpload(Long userId, MediaType mediaType, String s3Key, String contentType,
                           Instant expiresAt) {
        this.userId = userId;
        this.mediaType = mediaType;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getUserId() {
        return userId;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }

    public void markAttached(Instant attachedAt) {
        this.attachedAt = attachedAt;
    }
}
