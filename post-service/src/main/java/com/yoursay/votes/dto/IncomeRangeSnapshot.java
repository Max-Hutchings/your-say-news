package com.yoursay.votes.dto;

/** Identity-free provenance needed to resolve the exact local range captured with a vote. */
public record IncomeRangeSnapshot(
        Integer answerVersion,
        String profileId,
        Integer profileVersion,
        String marketCode,
        String currencyCode,
        String measure,
        String bandId,
        String relativeTier
) {
}
