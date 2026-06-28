package com.yoursay.votes;

/**
 * Inbound body for casting a vote. The voter's identity comes from the JWT, never the body.
 */
public record VoteRequestDto(Long postId, boolean voteFor) {
}
