package com.yoursay.votes.model;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Blocking Panache repository for votes. All methods run on virtual threads via the
 * JDBC datasource — no reactive session required.
 */
@ApplicationScoped
public class VoteRepository implements PanacheRepository<Vote> {

    /** Find a user's existing vote on a post; empty if none cast yet. */
    public Optional<Vote> findByPostAndUser(Long postId, Long userId) {
        return find("postId = ?1 and userId = ?2", postId, userId).firstResultOptional();
    }

    /** True when the user has already voted on this post (for the duplicate-vote guard). */
    public boolean existsByPostAndUser(Long postId, Long userId) {
        return count("postId = ?1 and userId = ?2", postId, userId) > 0;
    }

    /** All votes for a post — used by the sentiment aggregation engine. */
    public List<Vote> listByPost(Long postId) {
        return list("postId", postId);
    }
}
