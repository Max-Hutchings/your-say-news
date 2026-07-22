package com.yoursay.votes.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Minimal user-domain characteristic DTO consumed by the votes domain when freezing a vote-time
 * characteristic snapshot. The user id is intentionally not copied into the snapshot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserCharacteristicView(
        Long userId,
        String politicalPersuasion,
        String ageRange,
        String gender,
        String sexAtBirth,
        String sexualOrientation,
        String maritalStatus,
        List<String> race,
        String country,
        String region,
        String urbanRural,
        String ukCounty,
        String countryOfBirth,
        List<String> citizenship,
        String religion,
        String religiosity,
        String education,
        String occupation,
        String employmentSector,
        String universitySubject,
        String personalIncomeRange,
        String householdIncomeRange,
        String height,
        String weightRange,
        String eyeColor,
        String parent,
        Integer newsFrequency,
        Boolean hasPet,
        List<String> petType,
        String chronotype,
        String outlook,
        Boolean neurodivergent,
        List<String> neurodivergenceType,
        Boolean hasDisability,
        List<String> disabilityType,
        String housingStatus,
        String propertyType
) {
}
