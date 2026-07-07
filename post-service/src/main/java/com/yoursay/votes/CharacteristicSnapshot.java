package com.yoursay.votes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Set;

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
 * {@code null} field means "not captured".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        String personalIncomeRange,
        String householdIncomeRange,
        String height,
        String weightRange,
        String eyeColor,
        String parent,
        String newsFrequency,
        String hasPet,
        String petType,
        String chronotype,
        String outlook,
        String neurodivergent,
        String neurodivergenceType,
        String hasDisability,
        String disabilityType,
        String housingStatus,
        String propertyType
) {

    /** Sentinel bucket label for votes whose value on the requested axis is unknown. */
    public static final String UNKNOWN = "UNKNOWN";

    /**
     * The set of valid breakdown axes — every field name {@link #bucketFor(String)} recognises.
     * The single source of truth shared by the sentiment endpoint (to reject an unknown axis with a
     * 400 rather than return one misleading all-{@link #UNKNOWN} bucket) and its tests. Must stay in
     * lockstep with the {@code switch} in {@link #bucketFor(String)}.
     */
    public static final Set<String> AXES = Set.of(
            "politicalPersuasion", "ageRange", "gender", "sexAtBirth", "sexualOrientation",
            "maritalStatus", "race", "country", "region", "urbanRural", "ukCounty", "countryOfBirth",
            "citizenship", "religion", "religiosity", "education", "occupation", "employmentSector",
            "universitySubject", "personalIncomeRange", "householdIncomeRange", "height", "weightRange",
            "eyeColor", "parent", "newsFrequency", "hasPet", "petType", "chronotype", "outlook",
            "neurodivergent", "neurodivergenceType", "hasDisability", "disabilityType", "housingStatus",
            "propertyType");

    /** True if {@code axis} is a real breakdown axis (a field name aggregation can slice by). */
    public static boolean isAxis(String axis) {
        return AXES.contains(axis);
    }

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
            case "personalIncomeRange" -> personalIncomeRange;
            case "householdIncomeRange" -> householdIncomeRange;
            case "height" -> height;
            case "weightRange" -> weightRange;
            case "eyeColor" -> eyeColor;
            case "parent" -> parent;
            case "newsFrequency" -> newsFrequency;
            case "hasPet" -> hasPet;
            case "petType" -> petType;
            case "chronotype" -> chronotype;
            case "outlook" -> outlook;
            case "neurodivergent" -> neurodivergent;
            case "neurodivergenceType" -> neurodivergenceType;
            case "hasDisability" -> hasDisability;
            case "disabilityType" -> disabilityType;
            case "housingStatus" -> housingStatus;
            case "propertyType" -> propertyType;
            default -> null;
        };
        return value == null ? UNKNOWN : value;
    }

    /** An empty snapshot (all 36 axes unknown). */
    public static CharacteristicSnapshot empty() {
        return new CharacteristicSnapshot(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
