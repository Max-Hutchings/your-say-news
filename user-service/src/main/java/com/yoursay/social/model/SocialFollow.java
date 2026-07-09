package com.yoursay.social.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "social_follow",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_social_follow_pair",
                columnNames = {"follower_user_id", "followed_user_id"}))
public class SocialFollow extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "follower_user_id", nullable = false)
    private Long followerUserId;

    @Column(name = "followed_user_id", nullable = false)
    private Long followedUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SocialFollow() {
    }

    public SocialFollow(Long followerUserId, Long followedUserId) {
        this.followerUserId = followerUserId;
        this.followedUserId = followedUserId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getFollowerUserId() {
        return followerUserId;
    }

    public Long getFollowedUserId() {
        return followedUserId;
    }
}
