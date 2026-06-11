package com.yoursay.usercharacteristic;

/**
 * Public user-characteristic representation for HTTP and cross-domain use.
 *
 * Categorical values are strings so internal enum classes remain private to the domain.
 */
public record UserCharacteristicDto(
        Long id,
        Long userId,
        String postcode,
        String ukCounty,
        String race,
        String incomeRange,
        String countryOfBirth,
        String politicalPersuasion,
        String sexAtBirth,
        String height,
        String eyeColor,
        String weightRange,
        String parent,
        boolean universityEducated,
        String universitySubject,
        boolean propertyOwner
) {
}
