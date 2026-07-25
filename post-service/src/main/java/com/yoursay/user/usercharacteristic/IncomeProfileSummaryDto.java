package com.yoursay.user.usercharacteristic;

import java.util.List;

/** Small profile descriptor included in the main onboarding catalogue. */
public record IncomeProfileSummaryDto(
        String profileId,
        int profileVersion,
        String marketCode,
        String marketLabel,
        String currencyCode,
        List<String> residenceCountryCodes
) {
}
