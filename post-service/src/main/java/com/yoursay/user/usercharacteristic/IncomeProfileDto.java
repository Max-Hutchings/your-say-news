package com.yoursay.user.usercharacteristic;

import java.util.List;

/** Versioned personal and household income bands for one reviewed market/currency profile. */
public record IncomeProfileDto(
        String catalogVersion,
        String profileId,
        int profileVersion,
        String marketCode,
        String marketLabel,
        String currencyCode,
        List<String> residenceCountryCodes,
        String sourceYear,
        String sourceUrl,
        String derivation,
        String confidence,
        List<IncomeBandDto> personalBands,
        List<IncomeBandDto> householdBands
) {
}
