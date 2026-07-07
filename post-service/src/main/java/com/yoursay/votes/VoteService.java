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
     * Guard that a post can be voted on before any write is attempted: the id must be present
     * (else 400) and must reference a real post (else 404). Kept separate from {@link #castVote}
     * so the post-existence read runs outside the vote's write transaction.
     *
     * @param postId the post id from the request body (may be {@code null})
     * @throws jakarta.ws.rs.ClientErrorException 400 if {@code postId} is null, 404 if no such post
     */
    void assertVotablePost(Long postId);

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

    /**
     * Gate access to a post's aggregated sentiment results (the Stage 4 "results unlocked after
     * voting" product rule, enforced server-side as defence in depth). Passes silently when the
     * caller may see the results; otherwise throws before any aggregate is computed.
     *
     * @param postId        the post whose results are being requested
     * @param callerEmail   the authenticated user's email (from the JWT principal)
     * @param authorization the caller's {@code Authorization} header, forwarded to user-service
     * @throws jakarta.ws.rs.ClientErrorException 404 if the post does not exist, 403 if the caller
     *         has not yet voted on it
     */
    void assertResultsUnlocked(Long postId, String callerEmail, String authorization);
}
