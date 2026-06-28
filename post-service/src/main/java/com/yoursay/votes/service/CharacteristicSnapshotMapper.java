package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.client.UserCharacteristicView;

import java.util.List;

/**
 * Freezes a live user-characteristic response into the anonymised vote-time snapshot used for
 * aggregation. Identity fields from user-service are deliberately discarded.
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
                joinedRace(view.race()),
                view.country(),
                view.region(),
                view.urbanRural(),
                view.ukCounty(),
                view.countryOfBirth(),
                view.citizenship(),
                view.religion(),
                view.religiosity(),
                view.education(),
                view.occupation(),
                view.employmentSector(),
                view.universitySubject(),
                view.incomeRange(),
                view.height(),
                view.weightRange(),
                view.eyeColor(),
                view.parent(),
                view.newsFrequency() == null ? null : String.valueOf(view.newsFrequency())
        );
    }

    private static String joinedRace(List<String> race) {
        if (race == null || race.isEmpty()) {
            return null;
        }
        return race.stream()
                .sorted()
                .reduce((left, right) -> left + "+" + right)
                .orElse(null);
    }
}
