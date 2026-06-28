package com.yoursay.votes;

/**
 * HTTP response shape for vote endpoints. Deliberately omits {@code userId} and the
 * {@link CharacteristicSnapshot} — those are internal aggregation inputs, not caller-facing data.
 *
 * <p><strong>PII boundary:</strong> the response says nothing about who voted, only that a vote
 * exists for this post with this stance.
 */
public record VoteResponseDto(Long id, Long postId, boolean voteFor) {
}
