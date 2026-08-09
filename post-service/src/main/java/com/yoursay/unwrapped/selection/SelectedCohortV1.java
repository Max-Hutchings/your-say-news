package com.yoursay.unwrapped.selection;

import com.yoursay.votes.dto.CohortDimensionV1;
import java.util.List;

/**
 * A statistically safe cohort shortlisted as possible context for one voting option.
 *
 * @param cohortId stable identifier the model must copy when referencing the cohort
 * @param dimensions characteristic axis/value pairs that define cohort membership
 * @param role deterministic narrative slot used to keep the shortlist useful and varied
 * @param relevanceReason server-written explanation of why the cohort was selected
 * @param sampleSize number of post voters in the cohort; establishes evidence strength
 * @param populationSharePercentage cohort's share of all post voters; filters niche groups
 * @param optionVoteCount cohort members who selected this option
 * @param compositionPercentage share of this option's voters belonging to the cohort
 * @param propensityPercentage share of the cohort selecting this option
 * @param overIndexPercentagePoints cohort propensity minus the option's overall vote share
 * @param differenceFromRestPercentagePoints cohort propensity minus non-cohort propensity
 * @param wilson95Low lower 95% confidence bound for cohort propensity
 * @param wilson95High upper 95% confidence bound for cohort propensity
 * @param adjustedQValue multiple-comparison-adjusted significance used to limit false discoveries
 */
public record SelectedCohortV1(
        String cohortId,
        List<CohortDimensionV1> dimensions,
        CandidateRole role,
        String relevanceReason,
        long sampleSize,
        double populationSharePercentage,
        long optionVoteCount,
        double compositionPercentage,
        double propensityPercentage,
        double overIndexPercentagePoints,
        double differenceFromRestPercentagePoints,
        double wilson95Low,
        double wilson95High,
        double adjustedQValue,
        String displayName
) {
    public SelectedCohortV1(
            String cohortId,
            List<CohortDimensionV1> dimensions,
            CandidateRole role,
            String relevanceReason,
            long sampleSize,
            double populationSharePercentage,
            long optionVoteCount,
            double compositionPercentage,
            double propensityPercentage,
            double overIndexPercentagePoints,
            double differenceFromRestPercentagePoints,
            double wilson95Low,
            double wilson95High,
            double adjustedQValue
    ) {
        this(cohortId, dimensions, role, relevanceReason, sampleSize,
                populationSharePercentage, optionVoteCount, compositionPercentage,
                propensityPercentage, overIndexPercentagePoints,
                differenceFromRestPercentagePoints, wilson95Low, wilson95High,
                adjustedQValue, CohortDisplayNames.describe(dimensions));
    }
}
