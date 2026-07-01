package com.yoursay.usercharacteristic;

import java.util.List;

/**
 * Public user-characteristic representation for HTTP and cross-domain use.
 *
 * <p>Categorical values are strings (enum names) so the domain's internal enum classes stay
 * private. Keys mirror the frontend {@code CharacteristicAnswers} payload exactly.
 *
 * <p><strong>PII boundary:</strong> on inbound save requests the body carries ONLY characteristic
 * answers — {@code id} and {@code userId} are ignored and resolved server-side from the
 * authenticated subject. They are populated only on responses.
 */
public record UserCharacteristicDto(
        Long id,
        Long userId,
        // Location
        String country,
        String city,
        String region,
        String ukCounty,
        String urbanRural,
        // Who you are
        String ageRange,
        String gender,
        String genderSelfDescribe,
        String sexAtBirth,
        String sexualOrientation,
        String maritalStatus,
        List<String> race,
        // Background
        String countryOfBirth,
        String citizenship,
        String religion,
        String religiosity,
        String politicalPersuasion,
        // Education & work
        String education,
        String occupation,
        String employmentSector,
        String universitySubject,
        // Finances & body
        String personalIncomeRange,
        String householdIncomeRange,
        String height,
        String weightRange,
        String eyeColor,
        String parent,
        Integer newsFrequency,
        // Lifestyle
        Boolean hasPet,
        String petType,
        // Quirky
        String chronotype,
        String outlook,
        // Neurodiversity & disability
        Boolean neurodivergent,
        String neurodivergenceType,
        Boolean hasDisability,
        String disabilityType,
        // Property
        String housingStatus,
        String propertyType
) {
}
