package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.yoursay.posts.VotingType;

@Entity
@Table(name = "post")
public class Post extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    @Column(name = "jurisdiction", nullable = false, length = 32)
    private String jurisdiction = "GLOBAL";

    @Enumerated(EnumType.STRING)
    @Column(name = "voting_type", nullable = false, length = 32)
    private VotingType votingType = VotingType.BINARY;

    @Column(name = "is_ai_generated", nullable = false)
    private boolean isAiGenerated;

    @Column(name = "ai_draft_id")
    private UUID aiDraftId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    private List<PostMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    private List<PostVoteOption> voteOptions = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    private List<PostSource> sources = new ArrayList<>();

    public Post() {
    }

    public Post(Long userId, String summary, String supportQuestion, boolean isAiGenerated) {
        this.userId = userId;
        this.summary = summary;
        this.supportQuestion = supportQuestion;
        this.isAiGenerated = isAiGenerated;
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

    public void addSource(PostSource source) {
        source.setPost(this);
        sources.add(source);
    }

    public void configureVoting(VotingType votingType, List<VotingOptionRules.Definition> definitions) {
        this.votingType = votingType == null ? VotingType.BINARY : votingType;
        voteOptions.clear();
        definitions.forEach(definition -> voteOptions.add(new PostVoteOption(
                this, definition.label(), definition.ordinal(), definition.semanticKey())));
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

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public boolean isAiGenerated() {
        return isAiGenerated;
    }

    public void setAiGenerated(boolean aiGenerated) {
        isAiGenerated = aiGenerated;
    }

    public UUID getAiDraftId() { return aiDraftId; }
    public void setAiDraftId(UUID aiDraftId) { this.aiDraftId = aiDraftId; }
    public List<PostSource> getSources() { return sources; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PostMedia> getMedia() {
        return media;
    }

    public VotingType getVotingType() { return votingType; }
    public List<PostVoteOption> getVoteOptions() { return voteOptions; }
}
