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
 * <p>Categorical values are strings (enum names from user-service, or {@code "true"}/
 * {@code "false"} for boolean axes) so the votes domain stays decoupled from user-service's
 * internal enums. A {@code null} field means "not captured / prefer not to say".
 */
public record CharacteristicSnapshot(
        String politicalPersuasion,
        String incomeRange,
        String race,
        String sexAtBirth,
        String countryOfBirth,
        String ukCounty,
        String height,
        String eyeColor,
        String weightRange,
        String parent,
        String universitySubject,
        boolean universityEducated,
        boolean propertyOwner
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
            case "incomeRange" -> incomeRange;
            case "race" -> race;
            case "sexAtBirth" -> sexAtBirth;
            case "countryOfBirth" -> countryOfBirth;
            case "ukCounty" -> ukCounty;
            case "height" -> height;
            case "eyeColor" -> eyeColor;
            case "weightRange" -> weightRange;
            case "parent" -> parent;
            case "universitySubject" -> universitySubject;
            case "universityEducated" -> String.valueOf(universityEducated);
            case "propertyOwner" -> String.valueOf(propertyOwner);
            default -> null;
        };
        return value == null ? UNKNOWN : value;
    }

    /** An empty snapshot (all axes unknown). Placeholder until vote-time snapshotting lands in Stage 3. */
    public static CharacteristicSnapshot empty() {
        return new CharacteristicSnapshot(
                null, null, null, null, null, null, null, null, null, null, null, false, false);
    }
}
