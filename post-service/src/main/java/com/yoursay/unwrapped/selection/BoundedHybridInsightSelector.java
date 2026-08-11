package com.yoursay.unwrapped.selection;

import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.votes.dto.CohortAggregateV1;
import com.yoursay.votes.dto.MembershipSemantics;
import com.yoursay.votes.dto.OptionStatisticV1;
import com.yoursay.votes.dto.OverallOptionStatisticV1;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class BoundedHybridInsightSelector implements InsightSelectionService {
    private static final Set<String> CORE_AXES = Set.of(
            "ageRange", "gender", "politicalPersuasion", "country", "region", "urbanRural",
            "personalIncomeRange", "householdIncomeRange", "education", "occupation",
            "employmentSector");
    private static final Set<Set<String>> ALLOWED_INTERSECTION_AXES = Set.of(
            Set.of("ageRange", "gender"),
            Set.of("ageRange", "occupation"),
            Set.of("ageRange", "employmentSector"),
            Set.of("personalIncomeRange", "gender"),
            Set.of("politicalPersuasion", "personalIncomeRange"));
    private static final List<String> NARRATIVE_INSTRUCTIONS = List.of(
            "Explain why a selected cohort is likely to favour the option using researched context.",
            "Do not claim direct knowledge of every individual voter's private motivation.",
            "Do not claim this self-selected audience represents a jurisdiction's population.",
            "Do not introduce a cohort absent from this shortlist.");

    @Override
    public UnwrappedAnalysisBriefV1 select(PostAnalysisAggregateV1 aggregate) {
        List<OptionBriefV1> options = aggregate.options().stream()
                .map(option -> briefForOption(aggregate, option))
                .toList();
        return new UnwrappedAnalysisBriefV1("unwrapped-analysis-brief-v1", aggregate.postId(),
                aggregate.summary(), aggregate.question(), aggregate.jurisdiction(), aggregate.canonicalVoteCount(),
                aggregate.aggregateVersion(), options);
    }

    /**
     * One option's shortlist: its overall standing plus the handful of cohorts the narrator is
     * allowed to talk about. An option with no privacy-safe cohort says so instead of guessing.
     */
    private static OptionBriefV1 briefForOption(PostAnalysisAggregateV1 aggregate, VoteOptionDto option) {
        OverallOptionStatisticV1 overall = overallStatistic(aggregate, option.id());
        List<SelectedCohortV1> selected = selectRoles(eligible(aggregate, option.id()));
        String insufficient = selected.isEmpty()
                ? "No privacy-safe characteristic group is available."
                : null;
        return new OptionBriefV1(option, overall.count(), overall.percentage(), selected,
                NARRATIVE_INSTRUCTIONS, insufficient);
    }

    private static OverallOptionStatisticV1 overallStatistic(PostAnalysisAggregateV1 aggregate, Long optionId) {
        return aggregate.overall().stream()
                .filter(value -> value.optionId().equals(optionId))
                .findFirst()
                .orElse(new OverallOptionStatisticV1(optionId, 0, 0.0));
    }

    private static List<Eligible> eligible(PostAnalysisAggregateV1 aggregate, Long optionId) {
        if (aggregate.canonicalVoteCount() < aggregate.metadata().minimumOverallSample()) {
            return List.of();
        }
        return aggregate.cohorts().stream().flatMap(cohort -> cohort.options().stream()
                        .filter(stat -> stat.optionId().equals(optionId))
                        .filter(stat -> passesPrivacyFloors(aggregate, cohort))
                        .map(stat -> new Eligible(cohort, stat)))
                .sorted(Comparator
                        .comparingDouble((Eligible item) -> item.stat().differenceFromRestPercentagePoints())
                        .reversed()
                        .thenComparingDouble(item -> item.stat().adjustedQValue())
                        .thenComparing(item -> item.cohort().cohortId()))
                .toList();
    }

    private static boolean passesPrivacyFloors(PostAnalysisAggregateV1 aggregate,
                                               CohortAggregateV1 cohort) {
        int sampleFloor = cohort.dimensions().size() == 1
                ? aggregate.metadata().minimumCohortSample()
                : aggregate.metadata().minimumIntersectionSample();
        return cohort.sampleSize() >= sampleFloor
                && cohort.populationSharePercentage()
                >= aggregate.metadata().minimumCohortSharePercentage()
                && cohort.sampleSize() < aggregate.canonicalVoteCount();
    }

    private static List<SelectedCohortV1> selectRoles(List<Eligible> eligible) {
        List<SelectedCohortV1> result = new ArrayList<>();
        Set<String> used = new HashSet<>();

        eligible.stream()
                .filter(BoundedHybridInsightSelector::isSingleCore)
                .max(Comparator.comparingLong((Eligible item) -> item.cohort().sampleSize())
                        .thenComparingDouble(item -> item.stat().differenceFromRestPercentagePoints())
                        .thenComparing(item -> item.cohort().cohortId(), Comparator.reverseOrder()))
                .ifPresent(item -> add(result, used, item, CandidateRole.CORE_ANCHOR,
                        "Broad core group with strong coverage for this option."));

        eligible.stream()
                .filter(BoundedHybridInsightSelector::isSingleCore)
                .filter(item -> !used.contains(item.cohort().cohortId()))
                .findFirst()
                .ifPresent(item -> add(result, used, item, CandidateRole.CORE_DIFFERENTIATOR,
                        "Strongest available core differentiator for this option."));

        // TOPIC_RELEVANT intentionally remains empty until governed topic-to-axis mappings exist.

        eligible.stream()
                .filter(item -> item.cohort().dimensions().size() == 2)
                .filter(BoundedHybridInsightSelector::isAllowedIntersection)
                .filter(item -> !redundantWithUsed(item, eligible, used))
                .findFirst()
                .ifPresent(item -> add(result, used, item, CandidateRole.INTERSECTION_DISCOVERY,
                        "Strongest non-redundant allowlisted two-characteristic intersection."));

        return List.copyOf(result);
    }

    private static boolean isSingleCore(Eligible item) {
        return item.cohort().dimensions().size() == 1
                && CORE_AXES.contains(item.cohort().dimensions().getFirst().axis());
    }

    private static boolean isAllowedIntersection(Eligible item) {
        CohortAggregateV1 cohort = item.cohort();
        Set<String> axes = cohort.dimensions().stream()
                .map(value -> value.axis())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return cohort.membershipSemantics() == MembershipSemantics.EXCLUSIVE
                && axes.size() == 2
                && ALLOWED_INTERSECTION_AXES.contains(axes);
    }

    private static boolean redundantWithUsed(Eligible candidate, List<Eligible> eligible,
                                             Set<String> used) {
        return eligible.stream().filter(item -> used.contains(item.cohort().cohortId()))
                .anyMatch(item -> item.cohort().dimensions().stream()
                        .allMatch(candidate.cohort().dimensions()::contains)
                        && Math.abs(item.stat().differenceFromRestPercentagePoints()
                        - candidate.stat().differenceFromRestPercentagePoints()) < 2.0);
    }

    private static void add(List<SelectedCohortV1> target, Set<String> used, Eligible item,
                            CandidateRole role, String reason) {
        CohortAggregateV1 cohort = item.cohort();
        OptionStatisticV1 stat = item.stat();
        target.add(new SelectedCohortV1(cohort.cohortId(), cohort.dimensions(), role, reason,
                cohort.sampleSize(), cohort.populationSharePercentage(), stat.count(),
                stat.compositionPercentage(), stat.percentage(),
                stat.differenceFromOverallPercentagePoints(),
                stat.differenceFromRestPercentagePoints(), stat.wilson95Low(), stat.wilson95High(),
                stat.adjustedQValue()));
        used.add(cohort.cohortId());
    }

    private record Eligible(CohortAggregateV1 cohort, OptionStatisticV1 stat) {
    }
}
