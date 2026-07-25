package com.yoursay.votes.service;

import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.CohortAggregateV1;
import com.yoursay.votes.MembershipSemantics;
import com.yoursay.votes.OptionStatisticV1;
import com.yoursay.votes.OverallOptionStatisticV1;
import com.yoursay.votes.PostAnalysisAggregateV1;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostAnalysisAggregateBuilderTest {
    private static final Instant CAPTURED = Instant.parse("2026-07-25T12:00:00Z");
    private static final List<VoteOptionDto> OPTIONS = List.of(
            new VoteOptionDto(102L, "Keep public services funded", 1, null),
            new VoteOptionDto(101L, "Reduce public spending", 0, null));
    private final PostAnalysisAggregateBuilder builder = new PostAnalysisAggregateBuilder();

    @Test
    void aggregatesEveryRetainedNonNewsCharacteristicAndNoNewsHabitAxis() {
        PostAnalysisAggregateV1 aggregate = builder.build(post(),
                List.of(new VoteSnapshot(101L, fullNonNewsSnapshot())), 0, CAPTURED);

        assertEquals(PostAnalysisAggregateBuilder.SCHEMA_VERSION, aggregate.schemaVersion());
        assertEquals(42L, aggregate.postId());
        assertEquals(VotingType.MULTIPLE_CHOICE, aggregate.votingType());
        assertEquals("Tax and spending summary", aggregate.summary());
        assertEquals("Should public spending be reduced to lower income tax?", aggregate.question());
        assertEquals("GLOBAL", aggregate.jurisdiction());
        assertEquals(List.of(101L, 102L),
                aggregate.options().stream().map(VoteOptionDto::id).toList());
        assertEquals(1, aggregate.canonicalVoteCount());
        assertNull(aggregate.aggregateVersion());
        assertEquals(CAPTURED, aggregate.capturedAt());
        assertEquals(List.of(
                new OverallOptionStatisticV1(101L, 1, 100.0),
                new OverallOptionStatisticV1(102L, 0, 0.0)), aggregate.overall());

        List<CohortAggregateV1> singleAxisCohorts = aggregate.cohorts().stream()
                .filter(cohort -> cohort.dimensions().size() == 1)
                .toList();
        Map<String, String> bucketsByAxis = singleAxisCohorts.stream()
                .collect(Collectors.toMap(
                        cohort -> cohort.dimensions().getFirst().axis(),
                        cohort -> cohort.dimensions().getFirst().bucket()));

        assertEquals(Map.ofEntries(
                Map.entry("ageRange", "AGE_25_34"),
                Map.entry("gender", "WOMAN"),
                Map.entry("politicalPersuasion", "CENTRE"),
                Map.entry("country", "GB"),
                Map.entry("region", "LONDON"),
                Map.entry("urbanRural", "URBAN"),
                Map.entry("personalIncomeRange", "V2_TIER_3"),
                Map.entry("householdIncomeRange", "V2_TIER_4"),
                Map.entry("education", "MASTERS"),
                Map.entry("occupation", "EMPLOYED_FULL_TIME"),
                Map.entry("employmentSector", "IT_TECHNOLOGY"),
                Map.entry("sexAtBirth", "FEMALE"),
                Map.entry("sexualOrientation", "BISEXUAL"),
                Map.entry("maritalStatus", "MARRIED"),
                Map.entry("race", "EAST_ASIAN"),
                Map.entry("ukCounty", "GREATER_LONDON"),
                Map.entry("countryOfBirth", "INDIA"),
                Map.entry("citizenship", "BRITISH"),
                Map.entry("religion", "HINDU"),
                Map.entry("religiosity", "SOMEWHAT_IMPORTANT"),
                Map.entry("universitySubject", "COMPUTER_SCIENCE"),
                Map.entry("height", "CM_160_169"),
                Map.entry("weightRange", "KG_60_69"),
                Map.entry("eyeColor", "BROWN"),
                Map.entry("parent", "PARENT_CAREGIVER_UNDER_18"),
                Map.entry("hasPet", "true"),
                Map.entry("petType", "DOG"),
                Map.entry("chronotype", "NIGHT_OWL"),
                Map.entry("outlook", "OPTIMIST"),
                Map.entry("neurodivergent", "true"),
                Map.entry("neurodivergenceType", "ADHD"),
                Map.entry("hasDisability", "true"),
                Map.entry("disabilityType", "HEARING"),
                Map.entry("housingStatus", "OWN_MORTGAGE"),
                Map.entry("propertyType", "FLAT_APARTMENT")), bucketsByAxis);

        Set<String> multiSelectAxes = Set.of(
                "race", "citizenship", "petType", "neurodivergenceType", "disabilityType");
        Map<String, MembershipSemantics> semanticsByAxis = singleAxisCohorts.stream()
                .collect(Collectors.toMap(
                        cohort -> cohort.dimensions().getFirst().axis(),
                        CohortAggregateV1::membershipSemantics));
        assertEquals(bucketsByAxis.keySet().stream().collect(Collectors.toMap(
                        Function.identity(),
                        axis -> multiSelectAxes.contains(axis)
                                ? MembershipSemantics.MULTI_MEMBERSHIP
                                : MembershipSemantics.EXCLUSIVE)),
                semanticsByAxis);
    }

    @Test
    void computesExactCompositionPropensityOverIndexAndIntersectionStatistics() {
        PostAnalysisAggregateV1 aggregate = builder.build(post(), strongFixture(), 0, CAPTURED);

        assertEquals(200, aggregate.canonicalVoteCount());
        assertEquals(List.of(100L, 100L),
                aggregate.overall().stream().map(value -> value.count()).toList());
        assertEquals(List.of(50.0, 50.0),
                aggregate.overall().stream().map(value -> value.percentage()).toList());
        CohortAggregateV1 youngMen = cohort(aggregate, "ageRange=AGE_25_34&gender=MAN");
        OptionStatisticV1 support = option(youngMen, 101L);

        assertEquals(40, youngMen.sampleSize());
        assertEquals(20.0, youngMen.populationSharePercentage(), 0.000001);
        assertEquals(MembershipSemantics.EXCLUSIVE, youngMen.membershipSemantics());
        assertEquals(36, support.count());
        assertEquals(90.0, support.percentage(), 0.000001);
        assertEquals(36.0, support.compositionPercentage(), 0.000001);
        assertEquals(40.0, support.differenceFromOverallPercentagePoints(), 0.000001);
        assertEquals(50.0, support.differenceFromRestPercentagePoints(), 0.000001);
        assertEquals(76.95, support.wilson95Low(), 0.01);
        assertEquals(96.04, support.wilson95High(), 0.01);
        assertEquals("TWO_PROPORTION_Z", support.statisticalTest());
        assertTrue(support.adjustedQValue() > support.rawPValue(),
                "multiple-comparison correction must not return the raw p-value unchanged");
        assertEquals(5.3006728149642054E-8, support.adjustedQValue(), 1E-18);
        OptionStatisticV1 oppose = option(youngMen, 102L);
        assertEquals(4, oppose.count());
        assertEquals(10.0, oppose.percentage(), 0.000001);
        assertEquals(4.0, oppose.compositionPercentage(), 0.000001);
        assertEquals(-40.0, oppose.differenceFromOverallPercentagePoints(), 0.000001);
        assertEquals(-50.0, oppose.differenceFromRestPercentagePoints(), 0.000001);
        assertEquals(3.96, oppose.wilson95Low(), 0.01);
        assertEquals(23.05, oppose.wilson95High(), 0.01);
        assertEquals(5.3006728149642054E-8, oppose.adjustedQValue(), 1E-18);
        List<CohortAggregateV1> intersections = aggregate.cohorts().stream()
                .filter(cohort -> cohort.dimensions().size() == 2)
                .toList();
        assertEquals(11, intersections.size());
        assertEquals(Set.of(
                        Set.of("ageRange", "gender"),
                        Set.of("ageRange", "occupation"),
                        Set.of("ageRange", "employmentSector"),
                        Set.of("personalIncomeRange", "gender"),
                        Set.of("politicalPersuasion", "personalIncomeRange")),
                intersections.stream()
                        .map(cohort -> cohort.dimensions().stream()
                                .map(dimension -> dimension.axis())
                                .collect(Collectors.toSet()))
                        .collect(Collectors.toSet()));
        assertEquals(48, aggregate.metadata().testedComparisons());
        assertEquals("cohort-rules-v1", aggregate.metadata().ruleSetVersion());
        assertEquals(100, aggregate.metadata().minimumOverallSample());
        assertEquals(30, aggregate.metadata().minimumCohortSample());
        assertEquals(40, aggregate.metadata().minimumIntersectionSample());
        assertEquals(5.0, aggregate.metadata().minimumCohortSharePercentage());
        assertEquals(10.0, aggregate.metadata().minimumEffectPercentagePoints());
        assertEquals(0.05, aggregate.metadata().falseDiscoveryRate());
    }

    @Test
    void preservesEachMultiSelectMembershipInsteadOfCreatingAJoinedBucket() {
        CharacteristicSnapshot snapshot = snapshot("AGE_25_34", "WOMAN", "CENTRE",
                List.of("ASIAN", "WHITE"));
        PostAnalysisAggregateV1 aggregate = builder.build(post(),
                List.of(new VoteSnapshot(101L, snapshot)), 0, CAPTURED);

        CohortAggregateV1 asian = cohort(aggregate, "race=ASIAN");
        CohortAggregateV1 white = cohort(aggregate, "race=WHITE");
        assertEquals(MembershipSemantics.MULTI_MEMBERSHIP, asian.membershipSemantics());
        assertEquals(1, asian.sampleSize());
        assertEquals(1, white.sampleSize());
        assertTrue(aggregate.cohorts().stream()
                .noneMatch(value -> value.cohortId().contains("ASIAN+WHITE")));
    }

    @Test
    void appliesSuppressionToAggregateOutputWithoutChangingCanonicalTotals() {
        PostAnalysisAggregateV1 aggregate = builder.build(post(), List.of(
                new VoteSnapshot(101L, snapshot("AGE_25_34", "WOMAN", "CENTRE", List.of())),
                new VoteSnapshot(102L, snapshot("AGE_35_44", "MAN", "RIGHT", List.of()))
        ), 2, CAPTURED);

        assertEquals(2, aggregate.canonicalVoteCount());
        assertEquals(List.of(1L, 1L),
                aggregate.overall().stream().map(value -> value.count()).toList());
        assertTrue(aggregate.cohorts().stream()
                .noneMatch(value -> value.cohortId().startsWith("ageRange=")));
        assertEquals(16, aggregate.metadata().suppressedCohorts());
    }

    @Test
    void unknownSnapshotValuesNeverBecomeAgentCandidateCohorts() {
        PostAnalysisAggregateV1 aggregate = builder.build(post(),
                List.of(new VoteSnapshot(101L, CharacteristicSnapshot.empty())), 0, CAPTURED);

        assertEquals(List.of(), aggregate.cohorts());
        assertEquals(1, aggregate.overall().getFirst().count());
        assertEquals(0, aggregate.overall().get(1).count());
    }

    @Test
    void emptyVoteSetProducesFiniteZeroOverallStatisticsAndNoCohorts() {
        PostAnalysisAggregateV1 aggregate = builder.build(post(), List.of(), 0, CAPTURED);

        assertEquals(0, aggregate.canonicalVoteCount());
        assertEquals(List.of(
                new OverallOptionStatisticV1(101L, 0, 0.0),
                new OverallOptionStatisticV1(102L, 0, 0.0)), aggregate.overall());
        assertEquals(List.of(), aggregate.cohorts());
        assertEquals(0, aggregate.metadata().suppressedCohorts());
        assertEquals(0, aggregate.metadata().testedComparisons());
    }

    private static PostVotingConfigurationDto post() {
        return new PostVotingConfigurationDto(42L, "Tax and spending summary",
                "Should public spending be reduced to lower income tax?", "GLOBAL",
                VotingType.MULTIPLE_CHOICE, OPTIONS);
    }

    private static List<VoteSnapshot> strongFixture() {
        List<VoteSnapshot> votes = new ArrayList<>();
        add(votes, 36, 101L, "AGE_25_34", "MAN");
        add(votes, 28, 101L, "AGE_25_34", "WOMAN");
        add(votes, 24, 101L, "AGE_45_54", "MAN");
        add(votes, 12, 101L, "AGE_45_54", "WOMAN");
        add(votes, 4, 102L, "AGE_25_34", "MAN");
        add(votes, 12, 102L, "AGE_25_34", "WOMAN");
        add(votes, 36, 102L, "AGE_45_54", "MAN");
        add(votes, 48, 102L, "AGE_45_54", "WOMAN");
        return List.copyOf(votes);
    }

    private static void add(List<VoteSnapshot> target, int count, long optionId,
                            String age, String gender) {
        for (int i = 0; i < count; i++) {
            target.add(new VoteSnapshot(optionId, snapshot(age, gender, "CENTRE", List.of())));
        }
    }

    private static CohortAggregateV1 cohort(PostAnalysisAggregateV1 aggregate, String id) {
        return aggregate.cohorts().stream().filter(value -> value.cohortId().equals(id))
                .findFirst().orElseThrow();
    }

    private static OptionStatisticV1 option(CohortAggregateV1 cohort, long optionId) {
        return cohort.options().stream().filter(value -> value.optionId().equals(optionId))
                .findFirst().orElseThrow();
    }

    private static CharacteristicSnapshot snapshot(String age, String gender, String politics,
                                                   List<String> races) {
        CharacteristicSnapshot empty = CharacteristicSnapshot.empty();
        return new CharacteristicSnapshot(
                politics, age, gender, empty.sexAtBirth(), empty.sexualOrientation(),
                empty.maritalStatus(), races.isEmpty() ? null : String.join("+", races),
                "GB", "LONDON", "URBAN", empty.ukCounty(), empty.countryOfBirth(),
                empty.citizenship(), empty.religion(), empty.religiosity(), "DEGREE",
                "EMPLOYED", "TECHNOLOGY", empty.universitySubject(), "GBP_50_75",
                "GBP_75_100", empty.height(), empty.weightRange(), empty.eyeColor(), empty.parent(),
                "6_8", empty.hasPet(), empty.petType(), empty.chronotype(), empty.outlook(),
                empty.neurodivergent(), empty.neurodivergenceType(), empty.hasDisability(),
                empty.disabilityType(), empty.housingStatus(), empty.propertyType(),
                races, List.of(), List.of(), List.of(), List.of(), "true", "51_75");
    }

    private static CharacteristicSnapshot fullNonNewsSnapshot() {
        return new CharacteristicSnapshot(
                "CENTRE", "AGE_25_34", "WOMAN", "FEMALE", "BISEXUAL", "MARRIED",
                "EAST_ASIAN", "GB", "LONDON", "URBAN", "GREATER_LONDON", "INDIA",
                "BRITISH", "HINDU", "SOMEWHAT_IMPORTANT", "MASTERS",
                "EMPLOYED_FULL_TIME", "IT_TECHNOLOGY", "COMPUTER_SCIENCE", "V2_TIER_3",
                "V2_TIER_4", "CM_160_169", "KG_60_69", "BROWN",
                "PARENT_CAREGIVER_UNDER_18", "6_8", "true", "DOG", "NIGHT_OWL",
                "OPTIMIST", "true", "ADHD", "true", "HEARING", "OWN_MORTGAGE",
                "FLAT_APARTMENT", List.of("EAST_ASIAN"), List.of("BRITISH"),
                List.of("DOG"), List.of("ADHD"), List.of("HEARING"), "true", "51_75");
    }
}
