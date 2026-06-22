package com.yoursay.posts.model;

import com.yoursay.posts.MediaType;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * A single media item (image or video) attached to a {@link Post}. Stored only by reference: the
 * bytes live in S3 under {@code s3Key}; presigned URLs are minted at read time, never persisted.
 */
@Entity
@Table(name = "post_media")
public class PostMedia extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private MediaType mediaType;

    @Column(name = "s3_key", nullable = false, length = 1024)
    private String s3Key;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "poster_s3_key", length = 1024)
    private String posterS3Key;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PostMedia() {
    }

    public PostMedia(Post post, MediaType mediaType, String s3Key, String contentType,
                     String posterS3Key, int ordinal) {
        this.post = post;
        this.mediaType = mediaType;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.posterS3Key = posterS3Key;
        this.ordinal = ordinal;
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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getPosterS3Key() {
        return posterS3Key;
    }

    public void setPosterS3Key(String posterS3Key) {
        this.posterS3Key = posterS3Key;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
