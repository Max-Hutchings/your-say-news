package com.yoursay.votes;

import java.util.Optional;

/**
 * Public contract for the vote domain. All methods are blocking and run on virtual threads.
 *
 * <p>No method exposes raw vote rows: callers receive only the PII-safe {@link VoteResponseDto}
 * or aggregate counts. The {@link CharacteristicSnapshot} snapshot on each vote is an internal
 * aggregation input that never crosses the HTTP boundary.
 */
public interface VoteService {

    /**
     * Cast a vote on a post's support question.
     *
     * @param postId        the post being voted on
     * @param voteFor       {@code true} = support, {@code false} = oppose
     * @param callerEmail   the authenticated user's email (from the JWT principal claim)
     * @param authorization the caller's {@code Authorization} header, forwarded to user-service
     *                      for the role-gated user-id and characteristic lookups
     * @return the persisted vote (no snapshot, no userId in the response)
     * @throws jakarta.ws.rs.ClientErrorException with status 409 if the user has already voted
     */
    VoteResponseDto castVote(Long postId, boolean voteFor, String callerEmail, String authorization);

    /**
     * The authenticated user's existing vote on a post, if any.
     *
     * @param postId        the post to look up
     * @param callerEmail   the authenticated user's email
     * @param authorization the caller's {@code Authorization} header, forwarded to user-service
     * @return the vote, or empty if the user has not yet voted on this post
     */
    Optional<VoteResponseDto> getMyVote(Long postId, String callerEmail, String authorization);

    /** Total number of votes cast on a post (any stance). */
    long countForPost(Long postId);
}
