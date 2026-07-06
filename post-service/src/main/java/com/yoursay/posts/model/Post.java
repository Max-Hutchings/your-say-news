package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "post")
public class Post extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "support_question", nullable = false, length = 512)
    private String supportQuestion;

    /** One-line argument for the motion — the "case for" card. Optional. */
    @Column(name = "case_for", columnDefinition = "text")
    private String caseFor;

    /** One-line argument against the motion — the "case against" card. Optional. */
    @Column(name = "case_against", columnDefinition = "text")
    private String caseAgainst;

    @Column(name = "is_unbiased", nullable = false)
    private boolean isUnbiased;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    private List<PostMedia> media = new ArrayList<>();

    public Post() {
    }

    public Post(Long userId, String title, String summary, String supportQuestion, boolean isUnbiased) {
        this.userId = userId;
        this.title = title;
        this.summary = summary;
        this.supportQuestion = supportQuestion;
        this.isUnbiased = isUnbiased;
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

    /**
     * Attach a media item, assigning the next ordinal so read order matches insertion order.
     */
    public void addMedia(PostMedia item) {
        item.setPost(this);
        item.setOrdinal(media.size());
        media.add(item);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSupportQuestion() {
        return supportQuestion;
    }

    public void setSupportQuestion(String supportQuestion) {
        this.supportQuestion = supportQuestion;
    }

    public String getCaseFor() {
        return caseFor;
    }

    public void setCaseFor(String caseFor) {
        this.caseFor = caseFor;
    }

    public String getCaseAgainst() {
        return caseAgainst;
    }

    public void setCaseAgainst(String caseAgainst) {
        this.caseAgainst = caseAgainst;
    }

    public boolean isUnbiased() {
        return isUnbiased;
    }

    public void setUnbiased(boolean unbiased) {
        isUnbiased = unbiased;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PostMedia> getMedia() {
        return media;
    }
}
