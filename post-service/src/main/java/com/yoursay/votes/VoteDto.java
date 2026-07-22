package com.yoursay.votes;

/**
 * Internal vote representation carrying the full characteristic snapshot. Used within the votes
 * domain (e.g. service → repository) but never exposed directly over HTTP — use
 * {@link VoteResponseDto} for that.
 *
 * <p><strong>PII boundary:</strong> {@code userId} is present so internal logic can enforce
 * one-vote-per-user, but must never be serialised into an HTTP response.
 */
public record VoteDto(
        Long id,
        Long postId,
        Long optionId,
        Long userId,
        CharacteristicSnapshot snapshot
) {
}
