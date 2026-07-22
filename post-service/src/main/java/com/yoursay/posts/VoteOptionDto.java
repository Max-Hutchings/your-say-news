package com.yoursay.posts;

/** A stable, ordered option belonging to a post. */
public record VoteOptionDto(Long id, String label, int ordinal, String semanticKey) {
}
