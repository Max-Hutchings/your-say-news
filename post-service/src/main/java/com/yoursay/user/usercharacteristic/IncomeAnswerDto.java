package com.yoursay.user.usercharacteristic;

/**
 * Versioned income answer submitted without exact income. Reporting tiers are server-derived and
 * therefore deliberately absent from the request contract.
 */
public record IncomeAnswerDto(
        Integer answerVersion,
        String catalogVersion,
        String profileId,
        Integer profileVersion,
        String marketCode,
        String currencyCode,
        String personalBandId,
        String householdBandId
) {
}
