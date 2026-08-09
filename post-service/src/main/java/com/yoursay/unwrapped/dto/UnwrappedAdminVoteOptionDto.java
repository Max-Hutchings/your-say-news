package com.yoursay.unwrapped.dto;

/** One identity-free option in the overall vote split shown on the Unwrapped admin desk. */
public record UnwrappedAdminVoteOptionDto(
        Long optionId,
        String label,
        int ordinal,
        String semanticKey,
        long count,
        double percentage
) {
}
