package com.yoursay.unwrapped.selection;

import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.dto.AggregationMetadataV1;
import com.yoursay.votes.dto.CohortAggregateV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import com.yoursay.user.usercharacteristic.dto.IncomeRangeDisplayDto;
import com.yoursay.votes.dto.MembershipSemantics;
import com.yoursay.votes.dto.OptionStatisticV1;
import com.yoursay.votes.dto.OverallOptionStatisticV1;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoundedHybridInsightSelectorTest {
    private final BoundedHybridInsightSelector selector = new BoundedHybridInsightSelector();

    @Test
    void fillsBoundedRolesDeterministicallyAndNeverPassesEveryEligibleCohort() {
        PostAnalysisAggregateV1 aggregate = aggregate(250, List.of(
                cohort("gender=MAN", "gender", "MAN", 110, 18, 0.001),
                cohort("ageRange=AGE_25_34", "ageRange", "AGE_25_34", 80, 25, 0.002),
                cohort("occupation=EMPLOYED", "occupation", "EMPLOYED", 140, 12, 0.004),
                intersection("ageRange=AGE_25_34&gender=MAN", 45, 31, 0.003),
                intersection("ageRange=AGE_25_34&occupation=EMPLOYED",
                        List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                                new CohortDimensionV1("occupation", "EMPLOYED")),
                        MembershipSemantics.EXCLUSIVE, 44, 30, 0.002),
                cohort("eyeColor=BLUE", "eyeColor", "BLUE", 90, 29, 0.001)
        ));

        UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);
        OptionBriefV1 support = brief.options().getFirst();

        assertEquals("unwrapped-analysis-brief-v1", brief.schemaVersion());
        assertEquals(42L, brief.postId());
        assertEquals("A factual policy summary.", brief.summary());
        assertEquals("Should the policy change?", brief.question());
        assertEquals("GLOBAL", brief.jurisdiction());
        assertEquals(250, brief.canonicalVoteCount());
        assertEquals("sha256:fixture", brief.aggregateVersion());
        assertEquals(new VoteOptionDto(101L, "Support", 0, null), support.option());
        assertEquals(List.of("occupation=EMPLOYED", "ageRange=AGE_25_34",
                        "ageRange=AGE_25_34&gender=MAN"),
                support.candidates().stream().map(value -> value.cohortId()).toList());
        assertEquals(List.of(CandidateRole.CORE_ANCHOR, CandidateRole.CORE_DIFFERENTIATOR,
                        CandidateRole.INTERSECTION_DISCOVERY),
                support.candidates().stream().map(value -> value.role()).toList());
        assertEquals(List.of("Employed workers", "25–34-year-olds",
                        "25–34-year-olds and men"),
                support.candidates().stream().map(SelectedCohortV1::displayName).toList());
        assertEquals(new SelectedCohortV1(
                        "occupation=EMPLOYED",
                        List.of(new CohortDimensionV1("occupation", "EMPLOYED")),
                        CandidateRole.CORE_ANCHOR,
                        "Broad core group with strong coverage for this option.",
                        140, 56, 98, 65.33333333333333, 70, 10, 12,
                        60, 78, 0.004),
                support.candidates().getFirst());
        assertEquals(List.of(
                        "Broad core group with strong coverage for this option.",
                        "Strongest available core differentiator for this option.",
                        "Strongest non-redundant allowlisted two-characteristic intersection."),
                support.candidates().stream().map(SelectedCohortV1::relevanceReason).toList());
        assertEquals(150, support.overallVoteCount());
        assertEquals(60.0, support.overallVotePercentage());
        assertNull(support.insufficientEvidence());
        assertEquals(List.of(
                "Explain why a selected cohort is likely to favour the option using researched context.",
                "Do not claim direct knowledge of every individual voter's private motivation.",
                "Do not claim this self-selected audience represents a jurisdiction's population.",
                "Do not introduce a cohort absent from this shortlist."),
                support.narrativeInstructions());
        OptionBriefV1 oppose = brief.options().get(1);
        assertEquals(new VoteOptionDto(102L, "Oppose", 1, null), oppose.option());
        assertEquals(100, oppose.overallVoteCount());
        assertEquals(40.0, oppose.overallVotePercentage());
        assertEquals(List.of("occupation=EMPLOYED", "gender=MAN",
                        "ageRange=AGE_25_34&occupation=EMPLOYED"),
                oppose.candidates().stream().map(value -> value.cohortId()).toList());
        assertNull(oppose.insufficientEvidence());
    }

    @Test
    void ignoresEffectAndSignificanceThresholdsButKeepsPrivacySampleFloors() {
        PostAnalysisAggregateV1 aggregate = aggregate(100, List.of(
                cohort("gender=MAN", "gender", "MAN", 29, 65, 0.001),
                cohort("ageRange=AGE_25_34", "ageRange", "AGE_25_34", 40, 30, 0.051),
                cohort("occupation=EMPLOYED", "occupation", "EMPLOYED", 70, 9.99, 0.001)
        ));

        OptionBriefV1 support = selector.select(aggregate).options().getFirst();

        assertEquals(List.of("occupation=EMPLOYED", "ageRange=AGE_25_34"),
                support.candidates().stream().map(value -> value.cohortId()).toList());
        assertNull(support.insufficientEvidence());
    }

    @Test
    void suppliesTheSameAvailableGroupToEveryOptionWhenNeeded() {
        PostAnalysisAggregateV1 aggregate = aggregate(100, List.of(
                cohort("gender=MAN", "gender", "MAN", 60, 0, 1.0)
        ));

        UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);

        assertEquals(List.of("gender=MAN"),
                brief.options().getFirst().candidates().stream()
                        .map(SelectedCohortV1::cohortId).toList());
        assertEquals(List.of("gender=MAN"),
                brief.options().get(1).candidates().stream()
                        .map(SelectedCohortV1::cohortId).toList());
        assertEquals(new SelectedCohortV1(
                        "gender=MAN", List.of(new CohortDimensionV1("gender", "MAN")),
                        CandidateRole.CORE_ANCHOR,
                        "Broad core group with strong coverage for this option.",
                        60, 60, 42, 70, 70, 10, 0,
                        60, 78, 1.0),
                brief.options().getFirst().candidates().getFirst());
        assertEquals(new SelectedCohortV1(
                        "gender=MAN", List.of(new CohortDimensionV1("gender", "MAN")),
                        CandidateRole.CORE_ANCHOR,
                        "Broad core group with strong coverage for this option.",
                        60, 60, 18, 45, 30, -10, -0.0,
                        22, 40, 1.0),
                brief.options().get(1).candidates().getFirst());
        assertNull(brief.options().getFirst().insufficientEvidence());
        assertNull(brief.options().get(1).insufficientEvidence());
    }

    @Test
    void minimumOverallSampleIsAnInclusiveHundredVoteBoundary() {
        OptionBriefV1 belowMinimum = selector.select(aggregate(99, List.of(
                cohort("gender=MAN", "gender", "MAN", 60, 20, 0.001))))
                .options().getFirst();

        assertEquals(List.of(), belowMinimum.candidates());
        assertEquals("No privacy-safe characteristic group is available.",
                belowMinimum.insufficientEvidence());
        assertEquals(1, selector.select(aggregate(100, List.of(
                cohort("gender=MAN", "gender", "MAN", 60, 20, 0.001))))
                .options().getFirst().candidates().size());
    }

    @Test
    void privacyFloorsAreInclusiveForShareAndSingleOrIntersectionSamples() {
        assertEquals(1, selectedCount(cohort("gender=MAN", "gender", "MAN", 30, 0, 1.0)));
        assertEquals(0, selectedCount(cohort("gender=MAN", "gender", "MAN", 29, 65, 0.001)));
        assertEquals(1, selectedCount(intersection("ageRange=AGE_25_34&gender=MAN", 40, 0, 1.0)));
        assertEquals(0, selectedCount(intersection("ageRange=AGE_25_34&gender=MAN", 39, 65, 0.001)));
        assertEquals(0, selectedCount(cohort("country=UNITED_KINGDOM", "country",
                "UNITED_KINGDOM", 100, 50, 0.001), true));

        CohortAggregateV1 belowShare = new CohortAggregateV1(
                "gender=MAN", List.of(new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.EXCLUSIVE, 40, 4.99, statistics(40, 65, 0.001));
        assertEquals(0, selectedCount(belowShare, true));
        CohortAggregateV1 atShareFloor = new CohortAggregateV1(
                "gender=MAN", List.of(new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.EXCLUSIVE, 40, 5.0, statistics(40, 0, 1.0));
        assertEquals(1, selectedCount(atShareFloor, true));
    }

    @Test
    void rejectsUngovernedAndMultiMembershipIntersections() {
        CohortAggregateV1 ungoverned = intersection(
                "gender=MAN&religion=CHRISTIANITY",
                List.of(new CohortDimensionV1("gender", "MAN"),
                        new CohortDimensionV1("religion", "CHRISTIANITY")),
                MembershipSemantics.EXCLUSIVE, 40, 25, 0.001);
        CohortAggregateV1 ungovernedCorePair = intersection(
                "ageRange=AGE_25_34&region=LONDON",
                List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("region", "LONDON")),
                MembershipSemantics.EXCLUSIVE, 40, 25, 0.001);
        CohortAggregateV1 overlapping = intersection(
                "ageRange=AGE_25_34&gender=MAN",
                List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.MULTI_MEMBERSHIP, 40, 25, 0.001);

        OptionBriefV1 option = selector.select(aggregate(100,
                        List.of(ungoverned, ungovernedCorePair, overlapping)))
                .options().getFirst();

        assertEquals(List.of(), option.candidates());
        assertEquals("No privacy-safe characteristic group is available.",
                option.insufficientEvidence());
    }

    @Test
    void acceptsEveryGovernedExclusiveIntersectionInEitherDimensionOrder() {
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("gender", "MAN"),
                new CohortDimensionV1("ageRange", "AGE_25_34")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("ageRange", "AGE_25_34"),
                new CohortDimensionV1("occupation", "EMPLOYED")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("ageRange", "AGE_25_34"),
                new CohortDimensionV1("employmentSector", "HEALTHCARE")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("personalIncomeRange", "V2_TIER_3"),
                new CohortDimensionV1("gender", "MAN")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("politicalPersuasion", "CENTRE_LEFT"),
                new CohortDimensionV1("personalIncomeRange", "V2_TIER_3")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("householdIncomeRange", "V2_TIER_4"),
                new CohortDimensionV1("gender", "WOMAN")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("ageRange", "AGE_25_34"),
                new CohortDimensionV1("personalIncomeRange", "V2_TIER_3")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("ageRange", "AGE_25_34"),
                new CohortDimensionV1("householdIncomeRange", "V2_TIER_4")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("politicalPersuasion", "CENTRE_LEFT"),
                new CohortDimensionV1("ageRange", "AGE_25_34")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("politicalPersuasion", "CENTRE_LEFT"),
                new CohortDimensionV1("gender", "WOMAN")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("politicalPersuasion", "CENTRE_LEFT"),
                new CohortDimensionV1("householdIncomeRange", "V2_TIER_4")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("gender", "WOMAN"),
                new CohortDimensionV1("occupation", "EMPLOYED")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("gender", "WOMAN"),
                new CohortDimensionV1("employmentSector", "TECHNOLOGY")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("region", "LONDON"),
                new CohortDimensionV1("householdIncomeRange", "V2_TIER_4")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("region", "LONDON"),
                new CohortDimensionV1("employmentSector", "TECHNOLOGY")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("urbanRural", "URBAN"),
                new CohortDimensionV1("householdIncomeRange", "V2_TIER_4")));
        assertAllowlistedIntersection(List.of(
                new CohortDimensionV1("region", "LONDON"),
                new CohortDimensionV1("urbanRural", "URBAN")));

        OptionBriefV1 sensitiveSingle = selector.select(aggregate(100, List.of(
                cohort("religion=CHRISTIANITY", "religion", "CHRISTIANITY", 40, 25, 0.001))))
                .options().getFirst();
        assertEquals(List.of(), sensitiveSingle.candidates());
        assertEquals("No privacy-safe characteristic group is available.",
                sensitiveSingle.insufficientEvidence());
    }

    @Test
    void usesAdjustedQAndCohortIdAsDeterministicTieBreakers() {
        CohortAggregateV1 age = cohort(
                "ageRange=AGE_25_34", "ageRange", "AGE_25_34", 60, 20, 0.01);
        CohortAggregateV1 education = cohort(
                "education=BACHELORS", "education", "BACHELORS", 60, 20, 0.02);
        CohortAggregateV1 occupation = cohort(
                "occupation=EMPLOYED", "occupation", "EMPLOYED", 60, 20, 0.01);
        CohortAggregateV1 politics = cohort(
                "politicalPersuasion=CENTRE", "politicalPersuasion", "CENTRE", 60, 20, 0.01);

        List<String> expected = List.of("ageRange=AGE_25_34", "occupation=EMPLOYED");
        assertEquals(expected, selector.select(aggregate(200,
                        List.of(politics, occupation, education, age)))
                .options().getFirst().candidates().stream()
                .map(SelectedCohortV1::cohortId).toList());
        assertEquals(expected, selector.select(aggregate(200,
                        List.of(age, education, occupation, politics)))
                .options().getFirst().candidates().stream()
                .map(SelectedCohortV1::cohortId).toList());
    }

    @Test
    void treatsExactlyTwoPercentagePointsAsNonRedundant() {
        CohortAggregateV1 core = cohort("gender=MAN", "gender", "MAN", 60, 20, 0.001);
        CohortAggregateV1 belowBoundary = intersection(
                "ageRange=AGE_25_34&gender=MAN", 40, 21.99, 0.001);
        CohortAggregateV1 atBoundary = intersection(
                "ageRange=AGE_25_34&gender=MAN", 40, 22, 0.001);

        assertEquals(List.of("gender=MAN"), selector.select(aggregate(100,
                        List.of(core, belowBoundary))).options().getFirst().candidates().stream()
                .map(SelectedCohortV1::cohortId).toList());
        assertEquals(List.of("gender=MAN", "ageRange=AGE_25_34&gender=MAN"),
                selector.select(aggregate(100, List.of(core, atBoundary)))
                        .options().getFirst().candidates().stream()
                        .map(SelectedCohortV1::cohortId).toList());

        CohortAggregateV1 unrelated = intersection(
                "ageRange=AGE_25_34&occupation=EMPLOYED",
                List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("occupation", "EMPLOYED")),
                MembershipSemantics.EXCLUSIVE, 40, 21.99, 0.001);
        assertEquals(List.of("gender=MAN", "ageRange=AGE_25_34&occupation=EMPLOYED"),
                selector.select(aggregate(100, List.of(core, unrelated)))
                        .options().getFirst().candidates().stream()
                        .map(SelectedCohortV1::cohortId).toList());
    }

    @Test
    void turnsStoredBucketsIntoNaturalHeadlineReadyCohortNames() {
        assertEquals("Centre-left voters", name("politicalPersuasion", "CENTRE_LEFT"));
        assertEquals("Full-time workers", name("occupation", "EMPLOYED_FULL_TIME"));
        assertEquals("People with bachelor's degrees", name("education", "BACHELORS"));
        assertEquals("IT and technology workers", name("employmentSector", "IT_TECHNOLOGY"));
        IncomeRangeDisplayDto income = new IncomeRangeDisplayDto(
                "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
                "GBP 25k to GBP 40k", "Annual personal income before tax in the United Kingdom",
                "25th to 50th percentile locally", "GB", "United Kingdom", "GBP",
                "PERSONAL", "Annual personal income before tax", 25_000L, 40_000L,
                "TIER_3", "GB-GBP-GROSS-2025-v1", 1, "PERSONAL_TIER_3");
        CohortDimensionV1 dimension = new CohortDimensionV1(
                "personalIncomeRange", income.bucketId(), income.label(), income);
        CohortAggregateV1 incomeCohort = new CohortAggregateV1(
                "personalIncomeRange=" + income.bucketId(), List.of(dimension),
                MembershipSemantics.EXCLUSIVE, 40, 40, statistics(40, 20, 0.001));

        SelectedCohortV1 selected = selector.select(aggregate(100, List.of(incomeCohort)))
                .options().getFirst().candidates().getFirst();

        assertEquals(List.of(dimension), selected.dimensions());
        assertEquals("GBP 25k to GBP 40k", selected.dimensions().getFirst().income().label());
        assertEquals("People with annual personal income of GBP 25k to GBP 40k in the United Kingdom",
                selected.displayName());
    }

    private static String name(String axis, String bucket) {
        return CohortDisplayNames.describe(List.of(new CohortDimensionV1(axis, bucket)));
    }

    private void assertAllowlistedIntersection(List<CohortDimensionV1> dimensions) {
        String cohortId = dimensions.stream()
                .map(value -> value.axis() + "=" + value.bucket())
                .sorted()
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
        CohortAggregateV1 cohort = intersection(cohortId, dimensions,
                MembershipSemantics.EXCLUSIVE, 40, 20, 0.001);

        assertEquals(List.of(cohortId), selector.select(aggregate(100, List.of(cohort)))
                .options().getFirst().candidates().stream()
                .map(SelectedCohortV1::cohortId).toList());
    }

    private int selectedCount(CohortAggregateV1 cohort) {
        return selectedCount(cohort, false);
    }

    private int selectedCount(CohortAggregateV1 cohort, boolean preserveReportedShare) {
        return selector.select(aggregate(100, List.of(cohort), preserveReportedShare))
                .options().getFirst().candidates().size();
    }

    private static PostAnalysisAggregateV1 aggregate(long count, List<CohortAggregateV1> cohorts) {
        return aggregate(count, cohorts, false);
    }

    private static PostAnalysisAggregateV1 aggregate(
            long count, List<CohortAggregateV1> cohorts, boolean preserveReportedShare) {
        List<VoteOptionDto> options = List.of(
                new VoteOptionDto(101L, "Support", 0, null),
                new VoteOptionDto(102L, "Oppose", 1, null));
        long supportCount = Math.round(count * 0.6);
        long opposeCount = count - supportCount;
        double supportPercentage = 100.0 * supportCount / count;
        double opposePercentage = 100.0 * opposeCount / count;
        List<CohortAggregateV1> consistentCohorts = cohorts.stream()
                .map(cohort -> new CohortAggregateV1(cohort.cohortId(), cohort.dimensions(),
                        cohort.membershipSemantics(), cohort.sampleSize(),
                        preserveReportedShare ? cohort.populationSharePercentage()
                                : 100.0 * cohort.sampleSize() / count,
                        cohort.options().stream().map(stat -> {
                            long overallCount = stat.optionId().equals(101L)
                                    ? supportCount : opposeCount;
                            double overallPercentage = stat.optionId().equals(101L)
                                    ? supportPercentage : opposePercentage;
                            return new OptionStatisticV1(stat.optionId(), stat.count(),
                                    stat.percentage(), 100.0 * stat.count() / overallCount,
                                    stat.percentage() - overallPercentage,
                                    stat.differenceFromRestPercentagePoints(),
                                    stat.wilson95Low(), stat.wilson95High(), stat.rawPValue(),
                                    stat.adjustedQValue(), stat.statisticalTest());
                        }).toList()))
                .toList();
        return new PostAnalysisAggregateV1("post-analysis-aggregate-v1", 42L,
                VotingType.BINARY, "A factual policy summary.", "Should the policy change?", "GLOBAL", options, count,
                "sha256:fixture", Instant.parse("2026-07-25T12:00:00Z"),
                List.of(new OverallOptionStatisticV1(101L, supportCount, supportPercentage),
                        new OverallOptionStatisticV1(102L, opposeCount, opposePercentage)),
                consistentCohorts, new AggregationMetadataV1("cohort-rules-v1", 0, 100, 30, 40,
                5, 10, 0.05, 0, cohorts.size() * 2L));
    }

    private static CohortAggregateV1 cohort(String id, String axis, String bucket,
                                            long sample, double effect, double q) {
        return new CohortAggregateV1(id, List.of(new CohortDimensionV1(axis, bucket)),
                MembershipSemantics.EXCLUSIVE, sample, 20,
                statistics(sample, effect, q));
    }

    private static CohortAggregateV1 intersection(String id, long sample, double effect, double q) {
        return intersection(id,
                List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.EXCLUSIVE, sample, effect, q);
    }

    private static CohortAggregateV1 intersection(
            String id, List<CohortDimensionV1> dimensions, MembershipSemantics membershipSemantics,
            long sample, double effect, double q) {
        return new CohortAggregateV1(id, dimensions, membershipSemantics, sample, 10,
                statistics(sample, effect, q));
    }

    private static List<OptionStatisticV1> statistics(long sample, double effect, double q) {
        return List.of(
                new OptionStatisticV1(101L, Math.round(sample * 0.7), 70, 25, 20, effect,
                        60, 78, 0.001, q, "TWO_PROPORTION_Z"),
                new OptionStatisticV1(102L, Math.round(sample * 0.3), 30, 10, -20, -effect,
                        22, 40, 0.001, q, "TWO_PROPORTION_Z"));
    }
}
