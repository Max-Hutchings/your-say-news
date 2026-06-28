package com.yoursay.votes.model;

import com.yoursay.votes.CharacteristicSnapshot;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A single vote on a post's support question. Votes are append-only — no updates, no deletes.
 *
 * <p><strong>PII boundary:</strong> {@code userId} is stored so the unique constraint and
 * duplicate-vote check work, but it is never surfaced in any aggregation endpoint. The
 * {@link CharacteristicSnapshot} is a point-in-time, identity-free copy of the voter's
 * characteristics captured at vote time — aggregation reads from it, never from user-service.
 */
@Entity
@Table(
        name = "votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_votes_post_user", columnNames = {"post_id", "user_id"})
)
public class Vote extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "vote_for", nullable = false)
    private boolean voteFor;

    @Column(name = "characteristic_snapshot", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private CharacteristicSnapshot snapshot;

    protected Vote() {
    }

    public Vote(Long postId, Long userId, boolean voteFor, CharacteristicSnapshot snapshot) {
        this.postId = postId;
        this.userId = userId;
        this.voteFor = voteFor;
        this.snapshot = snapshot;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isVoteFor() {
        return voteFor;
    }

    public CharacteristicSnapshot getSnapshot() {
        return snapshot;
    }
}
