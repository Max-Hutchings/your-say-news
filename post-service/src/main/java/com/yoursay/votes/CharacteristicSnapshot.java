package com.yoursay.votes;

/**
 * An anonymised, point-in-time copy of the categorical characteristics a voter held
 * <em>at the moment they voted</em>. Frozen onto each vote so later profile edits never
 * retro-rewrite historical aggregates (see roadmap risk #4).
 *
 * <p><strong>PII boundary:</strong> this snapshot carries <em>no</em> identity — no user id,
 * name, email, exact DOB or postcode. It exists purely so {@link SentimentAggregator} can
 * slice sentiment by characteristic. Mirrors the reportable axes of the user-service
 * {@code UserCharacteristicDto}; identifying fields are deliberately excluded.
 *
 * <p>Categorical values are strings (enum names from user-service, or stringified numbers /
 * booleans) so the votes domain stays decoupled from user-service's internal enums. A
 * {@code null} field means "not captured / prefer not to say".
 */
public record CharacteristicSnapshot(
        String politicalPersuasion,
        String ageRange,
        String gender,
        String sexAtBirth,
        String sexualOrientation,
        String maritalStatus,
        String race,
        String country,
        String region,
        String urbanRural,
        String ukCounty,
        String countryOfBirth,
        String citizenship,
        String religion,
        String religiosity,
        String education,
        String occupation,
        String employmentSector,
        String universitySubject,
        String incomeRange,
        String height,
        String weightRange,
        String eyeColor,
        String parent,
        String newsFrequency
) {

    /** Sentinel bucket label for votes whose value on the requested axis is unknown. */
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * Resolve a single breakdown axis by its name (the field name, e.g. {@code "politicalPersuasion"})
     * to its bucket label. Used by the aggregation engine to group votes. Returns {@link #UNKNOWN}
     * when the axis is unrecognised or the value was not captured, so every vote always lands in
     * exactly one bucket and totals reconcile.
     */
    public String bucketFor(String axis) {
        String value = switch (axis) {
            case "politicalPersuasion" -> politicalPersuasion;
            case "ageRange" -> ageRange;
            case "gender" -> gender;
            case "sexAtBirth" -> sexAtBirth;
            case "sexualOrientation" -> sexualOrientation;
            case "maritalStatus" -> maritalStatus;
            case "race" -> race;
            case "country" -> country;
            case "region" -> region;
            case "urbanRural" -> urbanRural;
            case "ukCounty" -> ukCounty;
            case "countryOfBirth" -> countryOfBirth;
            case "citizenship" -> citizenship;
            case "religion" -> religion;
            case "religiosity" -> religiosity;
            case "education" -> education;
            case "occupation" -> occupation;
            case "employmentSector" -> employmentSector;
            case "universitySubject" -> universitySubject;
            case "incomeRange" -> incomeRange;
            case "height" -> height;
            case "weightRange" -> weightRange;
            case "eyeColor" -> eyeColor;
            case "parent" -> parent;
            case "newsFrequency" -> newsFrequency;
            default -> null;
        };
        return value == null ? UNKNOWN : value;
    }

    /** An empty snapshot (all 25 axes unknown). */
    public static CharacteristicSnapshot empty() {
        return new CharacteristicSnapshot(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
