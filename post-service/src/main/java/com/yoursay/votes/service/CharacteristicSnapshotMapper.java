package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.client.UserCharacteristicView;

import java.util.List;

/**
 * Freezes a live user-characteristic response into the anonymised vote-time snapshot used for
 * aggregation. Identity fields from the user domain are deliberately discarded.
 */
public final class CharacteristicSnapshotMapper {

    private CharacteristicSnapshotMapper() {
    }

    public static CharacteristicSnapshot from(UserCharacteristicView view) {
        if (view == null) {
            return CharacteristicSnapshot.empty();
        }
        return new CharacteristicSnapshot(
                view.politicalPersuasion(),
                view.ageRange(),
                view.gender(),
                view.sexAtBirth(),
                view.sexualOrientation(),
                view.maritalStatus(),
                joined(view.race()),
                view.country(),
                view.region(),
                view.urbanRural(),
                view.ukCounty(),
                view.countryOfBirth(),
                joined(view.citizenship()),
                view.religion(),
                view.religiosity(),
                view.education(),
                view.occupation(),
                view.employmentSector(),
                view.universitySubject(),
                view.personalIncomeRange(),
                view.householdIncomeRange(),
                view.height(),
                view.weightRange(),
                view.eyeColor(),
                view.parent(),
                view.newsFrequency() == null ? null : String.valueOf(view.newsFrequency()),
                view.hasPet() == null ? null : String.valueOf(view.hasPet()),
                joined(view.petType()),
                view.chronotype(),
                view.outlook(),
                view.neurodivergent() == null ? null : String.valueOf(view.neurodivergent()),
                joined(view.neurodivergenceType()),
                view.hasDisability() == null ? null : String.valueOf(view.hasDisability()),
                joined(view.disabilityType()),
                view.housingStatus(),
                view.propertyType()
        );
    }

    /**
     * Collapses a multi-select axis (ethnicity, nationality, pet, neurodivergence, disability) into a
     * single deterministic bucket label by sorting and joining with {@code +}, so a voter with the
     * same set always lands in the same bucket. {@code null}/empty becomes {@code null} (UNKNOWN).
     */
    private static String joined(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .sorted()
                .reduce((left, right) -> left + "+" + right)
                .orElse(null);
    }
}
