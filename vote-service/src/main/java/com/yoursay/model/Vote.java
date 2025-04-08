package com.yoursay.model;


import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
public class Vote extends PanacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private boolean voteFor;

    @Column(nullable=false)
    private Long userId;


    public Vote(Long id, Long postId, boolean voteFor, Long userId) {
        this.id = id;
        this.postId = postId;
        this.voteFor = voteFor;
        this.userId = userId;
    }

    public Vote(){}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public boolean isVoteFor() {
        return voteFor;
    }

    public void setVoteFor(boolean voteFor) {
        this.voteFor = voteFor;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
