package com.yoursay.posts;

import jakarta.persistence.*;


import java.time.Instant;

@Entity
@Table(name = "post_videos",
       indexes = {
           @Index(name = "idx_post_id", columnList = "post_id"),
           @Index(name = "idx_s3_key", columnList = "s3_key")
       })
public class PostVideo {

    public enum UploadStatus { UPLOADING, UPLOADED, PROCESSING, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "s3_key", nullable = false, length = 1024)
    @org.jetbrains.annotations.NotNull
    private String s3Key;              // object key in S3 (path)

    @Column(name = "bucket_name", nullable = false)
    private String bucketName;

    @Column(name = "url", length = 2048)
    private String url;                // optional pre-signed or public URL

    @Column(name = "filename")
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "duration_seconds")
    private Double durationSeconds;

    @Column(name = "width_px")
    private Integer widthPx;

    @Column(name = "height_px")
    private Integer heightPx;

    @Column(name = "checksum")
    private String checksum;           // e.g. md5/sha256 of the uploaded file

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadStatus status = UploadStatus.UPLOADING;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }

    public String getBucketName() { return bucketName; }
    public void setBucketName(String bucketName) { this.bucketName = bucketName; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Double getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Double durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getWidthPx() { return widthPx; }
    public void setWidthPx(Integer widthPx) { this.widthPx = widthPx; }

    public Integer getHeightPx() { return heightPx; }
    public void setHeightPx(Integer heightPx) { this.heightPx = heightPx; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public UploadStatus getStatus() { return status; }
    public void setStatus(UploadStatus status) { this.status = status; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}