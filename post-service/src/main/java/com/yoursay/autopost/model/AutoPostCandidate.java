package com.yoursay.autopost.model;

import com.yoursay.autopost.AutoPostRegion;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_post_candidate")
public class AutoPostCandidate extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Enumerated(EnumType.STRING)
    @Column(name = "region", nullable = false, length = 16)
    private AutoPostRegion region;

    @Column(name = "headline", nullable = false, length = 300)
    private String headline;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "deduplication_key", nullable = false, length = 160)
    private String deduplicationKey;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AutoPostCandidate() {
    }

    public AutoPostCandidate(UUID runId, int rank, AutoPostRegion region, String headline,
                             String summary, String deduplicationKey, Instant publishedAt) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.rank = rank;
        this.region = region;
        this.headline = headline.trim();
        this.summary = summary.trim();
        this.deduplicationKey = deduplicationKey.trim().toLowerCase(java.util.Locale.ROOT);
        this.publishedAt = publishedAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public int getRank() { return rank; }
    public AutoPostRegion getRegion() { return region; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public Instant getPublishedAt() { return publishedAt; }
}
