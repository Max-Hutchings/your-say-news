package com.yoursay.votes.service;

import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.votes.dto.AggregationMetadataV1;
import com.yoursay.votes.dto.CharacteristicSnapshot;
import com.yoursay.votes.dto.CohortAggregateV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import com.yoursay.votes.dto.MembershipSemantics;
import com.yoursay.votes.dto.OptionStatisticV1;
import com.yoursay.votes.dto.OverallOptionStatisticV1;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
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
    public static final String RULE_SET_VERSION = "cohort-rules-v2";

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
            List.of("politicalPersuasion", "personalIncomeRange"),
            List.of("householdIncomeRange", "gender"),
            List.of("ageRange", "personalIncomeRange"),
            List.of("ageRange", "householdIncomeRange"),
            List.of("politicalPersuasion", "ageRange"),
            List.of("politicalPersuasion", "gender"),
            List.of("politicalPersuasion", "householdIncomeRange"),
            List.of("gender", "occupation"),
            List.of("gender", "employmentSector"),
            List.of("region", "householdIncomeRange"),
            List.of("region", "employmentSector"),
            List.of("urbanRural", "householdIncomeRange"),
            List.of("region", "urbanRural"));

    public PostAnalysisAggregateV1 build(PostVotingConfigurationDto post, List<VoteSnapshot> votes,
                                         int suppressBelow, Instant capturedAt) {
        long total = votes.size();
        List<VoteOptionDto> options = sortOptionsByOrdinal(post.options());
        Map<Long, Long> overallCounts = countsByOption(votes);
        List<OverallOptionStatisticV1> overall = buildOverallStatistics(options, overallCounts, total);

        List<CohortWork> family = buildCohortFamily(votes);
        List<CohortWork> surfaced = selectCohortsAtOrAboveSuppressionFloor(family, suppressBelow);
        long suppressed = family.size() - surfaced.size();

        List<RawComparison> comparisons =
                compareCohortsAgainstRest(surfaced, votes, options, overallCounts, total);
        Map<String, List<OptionStatisticV1>> statisticsByCohort =
                adjustSignificanceAndGroupByCohort(comparisons);

        List<CohortAggregateV1> cohorts =
                createCohortAggregates(surfaced, statisticsByCohort, total);
        AggregationMetadataV1 metadata = new AggregationMetadataV1(
                RULE_SET_VERSION, suppressBelow, 100, 30, 40, 5.0, 10.0, 0.05,
                suppressed, comparisons.size());
        return new PostAnalysisAggregateV1(SCHEMA_VERSION, post.postId(), post.votingType(),
                post.summary(), post.question(), post.jurisdiction(), options, total, null, capturedAt, overall,
                cohorts, metadata);
    }

    private static List<VoteOptionDto> sortOptionsByOrdinal(List<VoteOptionDto> options) {
        return options.stream()
                .sorted(Comparator.comparingInt(VoteOptionDto::ordinal))
                .toList();
    }

    private static List<OverallOptionStatisticV1> buildOverallStatistics(
            List<VoteOptionDto> options,
            Map<Long, Long> overallCounts,
            long total
    ) {
        return options.stream()
                .map(option -> new OverallOptionStatisticV1(option.id(),
                        overallCounts.getOrDefault(option.id(), 0L),
                        percentage(overallCounts.getOrDefault(option.id(), 0L), total)))
                .toList();
    }

    /**
     * Every cohort the rules define — one per bucket of each reportable axis, plus the allowlisted
     * two-axis intersections — ordered by cohort id so the aggregate is byte-stable for a given
     * vote set.
     */
    private static List<CohortWork> buildCohortFamily(List<VoteSnapshot> votes) {
        List<CohortWork> family = new ArrayList<>();
        for (String axis : REPORTABLE_AXES) {
            family.addAll(singleAxis(axis, votes));
        }
        for (List<String> axes : INTERSECTION_AXES) {
            family.addAll(intersection(axes, votes));
        }
        family.sort(Comparator.comparing(CohortWork::cohortId));
        return family;
    }

    /** Privacy floor: a cohort smaller than the threshold is dropped before anything is reported. */
    private static List<CohortWork> selectCohortsAtOrAboveSuppressionFloor(
            List<CohortWork> family,
            int suppressBelow
    ) {
        return family.stream()
                .filter(cohort -> cohort.votes().size() >= suppressBelow)
                .toList();
    }

    private static List<RawComparison> compareCohortsAgainstRest(
            List<CohortWork> surfaced,
            List<VoteSnapshot> votes,
            List<VoteOptionDto> options,
            Map<Long, Long> overallCounts,
            long total
    ) {
        List<RawComparison> comparisons = new ArrayList<>();
        for (CohortWork cohort : surfaced) {
            List<VoteSnapshot> rest = votesOutsideCohort(votes, cohort);
            Map<Long, Long> cohortCounts = countsByOption(cohort.votes());
            Map<Long, Long> restCounts = countsByOption(rest);
            for (VoteOptionDto option : options) {
                comparisons.add(compareOption(cohort, option, cohortCounts, restCounts,
                        rest.size(), overallCounts, total));
            }
        }
        return comparisons;
    }

    private static List<VoteSnapshot> votesOutsideCohort(List<VoteSnapshot> votes, CohortWork cohort) {
        Set<VoteSnapshot> members = new LinkedHashSet<>(cohort.votes());
        return votes.stream().filter(vote -> !members.contains(vote)).toList();
    }

    private static RawComparison compareOption(
            CohortWork cohort,
            VoteOptionDto option,
            Map<Long, Long> cohortCounts,
            Map<Long, Long> restCounts,
            long restSize,
            Map<Long, Long> overallCounts,
            long total
    ) {
        long cohortSize = cohort.votes().size();
        long cohortCount = cohortCounts.getOrDefault(option.id(), 0L);
        long restCount = restCounts.getOrDefault(option.id(), 0L);
        long overallCount = overallCounts.getOrDefault(option.id(), 0L);
        PostAnalysisStatistics.TestResult test =
                PostAnalysisStatistics.compare(cohortCount, cohortSize, restCount, restSize);
        double[] interval = PostAnalysisStatistics.wilson95(cohortCount, cohortSize);
        double cohortPercentage = percentage(cohortCount, cohortSize);
        return new RawComparison(cohort.cohortId(), option.id(), cohortCount,
                cohortPercentage,
                percentage(cohortCount, overallCount),
                cohortPercentage - percentage(overallCount, total),
                cohortPercentage - percentage(restCount, restSize),
                interval[0], interval[1], test.pValue(), test.method());
    }

    /**
     * Apply the Benjamini-Hochberg correction across every comparison at once — the false discovery
     * rate is controlled over the whole family, not per cohort — then group the corrected
     * statistics under their cohort.
     */
    private static Map<String, List<OptionStatisticV1>> adjustSignificanceAndGroupByCohort(
            List<RawComparison> comparisons
    ) {
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
        return statisticsByCohort;
    }

    private static List<CohortAggregateV1> createCohortAggregates(
            List<CohortWork> surfaced,
            Map<String, List<OptionStatisticV1>> statisticsByCohort,
            long total
    ) {
        return surfaced.stream()
                .map(cohort -> new CohortAggregateV1(cohort.cohortId(), cohort.dimensions(),
                        cohort.membershipSemantics(), cohort.votes().size(),
                        percentage(cohort.votes().size(), total),
                        List.copyOf(statisticsByCohort.getOrDefault(cohort.cohortId(), List.of()))))
                .toList();
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
