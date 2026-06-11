package com.yoursay.votes;

/**
 * Public vote representation for HTTP and cross-domain use.
 */
public record VoteDto(
        Long id,
        Long postId,
        boolean voteFor,
        Long userId
) {
}
