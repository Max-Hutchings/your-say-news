package com.yoursay.posts.model;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "post_vote_option")
public class PostVoteOption extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "semantic_key", length = 32)
    private String semanticKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostVoteOption() {
    }

    public PostVoteOption(Post post, String label, int ordinal, String semanticKey) {
        this.post = post;
        this.label = label;
        this.ordinal = ordinal;
        this.semanticKey = semanticKey;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getLabel() { return label; }
    public int getOrdinal() { return ordinal; }
    public String getSemanticKey() { return semanticKey; }
}
