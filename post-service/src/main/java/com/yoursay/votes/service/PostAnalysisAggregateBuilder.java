package com.yoursay.votes.service;

import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.VoteOptionDto;
import com.yoursay.votes.AggregationMetadataV1;
import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.CohortAggregateV1;
import com.yoursay.votes.CohortDimensionV1;
import com.yoursay.votes.MembershipSemantics;
import com.yoursay.votes.OptionStatisticV1;
import com.yoursay.votes.OverallOptionStatisticV1;
import com.yoursay.votes.PostAnalysisAggregateV1;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, deterministic construction of the aggregate family searched by Post Unwrapped. */
@ApplicationScoped
public class PostAnalysisAggregateBuilder {
    public static final String SCHEMA_VERSION = "post-analysis-aggregate-v1";
    public static final String RULE_SET_VERSION = "cohort-rules-v1";

    /**
     * Every retained reportable characteristic except the four news-habit answers. Inclusion here
     * makes an axis available to deterministic analysis; the Unwrapped selector separately decides
     * which statistically safe cohorts are eligible for narration.
     */
    static final List<String> REPORTABLE_AXES = List.of(
            "ageRange", "gender", "politicalPersuasion", "country", "region", "urbanRural",
            "personalIncomeRange", "householdIncomeRange", "education", "occupation",
            "employmentSector", "sexAtBirth", "sexualOrientation", "maritalStatus", "race",
            "ukCounty", "countryOfBirth", "citizenship", "religion", "religiosity",
            "universitySubject", "height", "weightRange", "eyeColor", "parent", "hasPet",
            "petType", "chronotype", "outlook", "neurodivergent", "neurodivergenceType",
            "hasDisability", "disabilityType", "housingStatus", "propertyType");
    static final List<List<String>> INTERSECTION_AXES = List.of(
            List.of("ageRange", "gender"),
            List.of("ageRange", "occupation"),
            List.of("ageRange", "employmentSector"),
            List.of("personalIncomeRange", "gender"),
            List.of("politicalPersuasion", "personalIncomeRange"));

    public PostAnalysisAggregateV1 build(PostVotingConfigurationDto post, List<VoteSnapshot> votes,
                                         int suppressBelow, Instant capturedAt) {
        List<VoteOptionDto> options = post.options().stream()
                .sorted(Comparator.comparingInt(VoteOptionDto::ordinal))
                .toList();
        Map<Long, Long> overallCounts = countsByOption(votes);
        long total = votes.size();
        List<OverallOptionStatisticV1> overall = options.stream()
                .map(option -> new OverallOptionStatisticV1(option.id(),
                        overallCounts.getOrDefault(option.id(), 0L),
                        percentage(overallCounts.getOrDefault(option.id(), 0L), total)))
                .toList();

        List<CohortWork> family = new ArrayList<>();
        for (String axis : REPORTABLE_AXES) {
            family.addAll(singleAxis(axis, votes));
        }
        for (List<String> axes : INTERSECTION_AXES) {
            family.addAll(intersection(axes, votes));
        }
        family.sort(Comparator.comparing(CohortWork::cohortId));

        long suppressed = family.stream().filter(item -> item.votes().size() < suppressBelow).count();
        List<CohortWork> surfaced = family.stream()
                .filter(item -> item.votes().size() >= suppressBelow)
                .toList();

        List<RawComparison> comparisons = new ArrayList<>();
        for (CohortWork cohort : surfaced) {
            Set<VoteSnapshot> members = new LinkedHashSet<>(cohort.votes());
            List<VoteSnapshot> rest = votes.stream().filter(vote -> !members.contains(vote)).toList();
            Map<Long, Long> cohortCounts = countsByOption(cohort.votes());
            Map<Long, Long> restCounts = countsByOption(rest);
            for (VoteOptionDto option : options) {
                long cohortCount = cohortCounts.getOrDefault(option.id(), 0L);
                long restCount = restCounts.getOrDefault(option.id(), 0L);
                PostAnalysisStatistics.TestResult test = PostAnalysisStatistics.compare(
                        cohortCount, cohort.votes().size(), restCount, rest.size());
                double[] interval = PostAnalysisStatistics.wilson95(cohortCount, cohort.votes().size());
                comparisons.add(new RawComparison(cohort.cohortId(), option.id(), cohortCount,
                        percentage(cohortCount, cohort.votes().size()),
                        percentage(cohortCount, overallCounts.getOrDefault(option.id(), 0L)),
                        percentage(cohortCount, cohort.votes().size())
                                - percentage(overallCounts.getOrDefault(option.id(), 0L), total),
                        percentage(cohortCount, cohort.votes().size())
                                - percentage(restCount, rest.size()),
                        interval[0], interval[1], test.pValue(), test.method()));
            }
        }
        double[] qValues = PostAnalysisStatistics.benjaminiHochberg(
                comparisons.stream().map(RawComparison::pValue).toList());
        Map<String, List<OptionStatisticV1>> statisticsByCohort = new LinkedHashMap<>();
        for (int i = 0; i < comparisons.size(); i++) {
            RawComparison value = comparisons.get(i);
            statisticsByCohort.computeIfAbsent(value.cohortId(), ignored -> new ArrayList<>())
                    .add(new OptionStatisticV1(value.optionId(), value.count(), value.percentage(),
                            value.composition(), value.differenceFromOverall(), value.differenceFromRest(),
                            value.wilsonLow(), value.wilsonHigh(), value.pValue(), qValues[i],
                            value.testMethod()));
        }

        List<CohortAggregateV1> cohorts = surfaced.stream()
                .map(cohort -> new CohortAggregateV1(cohort.cohortId(), cohort.dimensions(),
                        cohort.membershipSemantics(), cohort.votes().size(),
                        percentage(cohort.votes().size(), total),
                        List.copyOf(statisticsByCohort.getOrDefault(cohort.cohortId(), List.of()))))
                .toList();
        AggregationMetadataV1 metadata = new AggregationMetadataV1(
                RULE_SET_VERSION, suppressBelow, 100, 30, 40, 5.0, 10.0, 0.05,
                suppressed, comparisons.size());
        return new PostAnalysisAggregateV1(SCHEMA_VERSION, post.postId(), post.votingType(),
                post.question(), post.jurisdiction(), options, total, null, capturedAt, overall,
                cohorts, metadata);
    }

    private static List<CohortWork> singleAxis(String axis, List<VoteSnapshot> votes) {
        Map<String, List<VoteSnapshot>> grouped = new LinkedHashMap<>();
        for (VoteSnapshot vote : votes) {
            for (String bucket : vote.snapshot().bucketsFor(axis)) {
                if (!CharacteristicSnapshot.UNKNOWN.equals(bucket)) {
                    grouped.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(vote);
                }
            }
        }
        MembershipSemantics semantics = CharacteristicSnapshot.isMultiSelectAxis(axis)
                ? MembershipSemantics.MULTI_MEMBERSHIP : MembershipSemantics.EXCLUSIVE;
        return grouped.entrySet().stream()
                .map(entry -> new CohortWork(id(List.of(new CohortDimensionV1(axis, entry.getKey()))),
                        List.of(new CohortDimensionV1(axis, entry.getKey())), semantics,
                        List.copyOf(entry.getValue())))
                .toList();
    }

    private static List<CohortWork> intersection(List<String> axes, List<VoteSnapshot> votes) {
        Map<String, CohortWorkBuilder> grouped = new LinkedHashMap<>();
        for (VoteSnapshot vote : votes) {
            List<CohortDimensionV1> dimensions = axes.stream()
                    .map(axis -> new CohortDimensionV1(axis, vote.snapshot().bucketFor(axis)))
                    .toList();
            if (dimensions.stream().anyMatch(dimension ->
                    CharacteristicSnapshot.UNKNOWN.equals(dimension.bucket()))) {
                continue;
            }
            String cohortId = id(dimensions);
            grouped.computeIfAbsent(cohortId, ignored -> new CohortWorkBuilder(dimensions))
                    .votes.add(vote);
        }
        return grouped.entrySet().stream()
                .map(entry -> new CohortWork(entry.getKey(), entry.getValue().dimensions,
                        MembershipSemantics.EXCLUSIVE, List.copyOf(entry.getValue().votes)))
                .toList();
    }

    private static String id(List<CohortDimensionV1> dimensions) {
        return dimensions.stream()
                .map(value -> value.axis() + "=" + value.bucket())
                .sorted()
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
    }

    private static Map<Long, Long> countsByOption(List<VoteSnapshot> votes) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        votes.forEach(vote -> counts.merge(vote.optionId(), 1L, Long::sum));
        return counts;
    }

    private static double percentage(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : 100.0 * numerator / denominator;
    }

    private record CohortWork(
            String cohortId,
            List<CohortDimensionV1> dimensions,
            MembershipSemantics membershipSemantics,
            List<VoteSnapshot> votes
    ) {
    }

    private static final class CohortWorkBuilder {
        private final List<CohortDimensionV1> dimensions;
        private final List<VoteSnapshot> votes = new ArrayList<>();

        private CohortWorkBuilder(List<CohortDimensionV1> dimensions) {
            this.dimensions = dimensions;
        }
    }

    private record RawComparison(
            String cohortId, Long optionId, long count, double percentage, double composition,
            double differenceFromOverall, double differenceFromRest, double wilsonLow,
            double wilsonHigh, double pValue, String testMethod
    ) {
    }
}
