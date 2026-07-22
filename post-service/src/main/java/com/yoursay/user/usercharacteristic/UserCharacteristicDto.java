package com.yoursay.user.usercharacteristic;

import java.util.List;

/**
 * Public user-characteristic representation for HTTP and cross-domain use.
 *
 * <p>Categorical values are strings (enum names) so the domain's internal enum classes stay
 * private. Keys mirror the frontend {@code CharacteristicAnswers} payload exactly.
 *
 * <p><strong>Age:</strong> inbound requests carry {@code age} (an integer, min 16). The server stores
 * only the derived birth year (see ADR-017); on responses it returns both the recomputed {@code age}
 * and the derived reporting band {@code ageRange}. Exact DOB is never stored or reported.
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
        Integer age,
        String ageRange,
        String gender,
        String genderSelfDescribe,
        String sexAtBirth,
        String sexualOrientation,
        String maritalStatus,
        List<String> race,
        // Background
        String countryOfBirth,
        List<String> citizenship,
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
        // Lifestyle
        Boolean hasPet,
        List<String> petType,
        // Quirky
        String chronotype,
        String outlook,
        // Neurodiversity & disability
        Boolean neurodivergent,
        List<String> neurodivergenceType,
        Boolean hasDisability,
        List<String> disabilityType,
        // Housing
        String housingStatus,
        String propertyType,
        // News habits
        Integer newsFrequency,
        Boolean balancedNewsViewpoint,
        Integer mainstreamNewsPercent,
        Boolean betterWorldWithData
) {
}
