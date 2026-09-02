package com.yoursay.user.usercharacteristic.dto;

/** Public, identity-free display metadata for one immutable country-specific income band. */
public record IncomeRangeDisplayDto(
        String bucketId,
        String label,
        String contextLabel,
        String relativeLabel,
        String marketCode,
        String marketLabel,
        String currencyCode,
        String measure,
        String measureLabel,
        Long lowerInclusive,
        Long upperExclusive,
        String relativeTier,
        String profileId,
        int profileVersion,
        String bandId
) {
}
