package com.yoursay.unwrapped.selection;

import com.yoursay.unwrapped.CandidateRole;
import com.yoursay.unwrapped.InsightSelectionService;
import com.yoursay.unwrapped.OptionBriefV1;
import com.yoursay.unwrapped.SelectedCohortV1;
import com.yoursay.unwrapped.UnwrappedAnalysisBriefV1;
import com.yoursay.votes.CohortAggregateV1;
import com.yoursay.votes.OptionStatisticV1;
import com.yoursay.votes.OverallOptionStatisticV1;
import com.yoursay.votes.PostAnalysisAggregateV1;
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
    private static final List<String> PROHIBITED = List.of(
            "Do not claim that a demographic characteristic caused a vote.",
            "Do not claim this self-selected audience represents a jurisdiction's population.",
            "Do not introduce a cohort absent from this shortlist.",
            "Distinguish observed vote data, external context, and interpretation.");

    @Override
    public UnwrappedAnalysisBriefV1 select(PostAnalysisAggregateV1 aggregate) {
        List<OptionBriefV1> options = aggregate.options().stream().map(option -> {
            OverallOptionStatisticV1 overall = aggregate.overall().stream()
                    .filter(value -> value.optionId().equals(option.id()))
                    .findFirst()
                    .orElse(new OverallOptionStatisticV1(option.id(), 0, 0.0));
            List<Eligible> eligible = eligible(aggregate, option.id());
            List<SelectedCohortV1> selected = selectRoles(eligible);
            String insufficient = selected.isEmpty()
                    ? "No reliable demographic concentration passes the versioned narration rules."
                    : null;
            return new OptionBriefV1(option, overall.count(), overall.percentage(), selected,
                    researchQuestions(aggregate.question(), option.label(), selected),
                    PROHIBITED, insufficient);
        }).toList();
        return new UnwrappedAnalysisBriefV1("unwrapped-analysis-brief-v1", aggregate.postId(),
                aggregate.summary(), aggregate.question(), aggregate.jurisdiction(), aggregate.canonicalVoteCount(),
                aggregate.aggregateVersion(), options);
    }

    private static List<Eligible> eligible(PostAnalysisAggregateV1 aggregate, Long optionId) {
        if (aggregate.canonicalVoteCount() < aggregate.metadata().minimumOverallSample()) {
            return List.of();
        }
        return aggregate.cohorts().stream().flatMap(cohort -> cohort.options().stream()
                        .filter(stat -> stat.optionId().equals(optionId))
                        .filter(stat -> passes(aggregate, cohort, stat))
                        .map(stat -> new Eligible(cohort, stat)))
                .sorted(Comparator
                        .comparingDouble((Eligible item) -> item.stat().differenceFromRestPercentagePoints())
                        .reversed()
                        .thenComparingDouble(item -> item.stat().adjustedQValue())
                        .thenComparing(item -> item.cohort().cohortId()))
                .toList();
    }

    private static boolean passes(PostAnalysisAggregateV1 aggregate, CohortAggregateV1 cohort,
                                  OptionStatisticV1 statistic) {
        int sampleFloor = cohort.dimensions().size() == 1
                ? aggregate.metadata().minimumCohortSample()
                : aggregate.metadata().minimumIntersectionSample();
        return cohort.sampleSize() >= sampleFloor
                && cohort.populationSharePercentage()
                >= aggregate.metadata().minimumCohortSharePercentage()
                && statistic.differenceFromRestPercentagePoints()
                >= aggregate.metadata().minimumEffectPercentagePoints()
                && statistic.adjustedQValue() <= aggregate.metadata().falseDiscoveryRate();
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
                        "Broad core group with strong coverage and positive over-representation."));

        eligible.stream()
                .filter(BoundedHybridInsightSelector::isSingleCore)
                .filter(item -> !used.contains(item.cohort().cohortId()))
                .findFirst()
                .ifPresent(item -> add(result, used, item, CandidateRole.CORE_DIFFERENTIATOR,
                        "Strongest statistically safe core differentiator."));

        // TOPIC_RELEVANT intentionally remains empty until governed topic-to-axis mappings exist.

        eligible.stream()
                .filter(item -> item.cohort().dimensions().size() == 2)
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

    private static List<String> researchQuestions(String question, String option,
                                                  List<SelectedCohortV1> selected) {
        List<String> result = new ArrayList<>();
        result.add("What current official data supports the strongest responsible case for '"
                + option + "' on: " + question);
        selected.forEach(cohort -> result.add(
                "What credible evidence may contextualise " + cohort.cohortId()
                        + " without claiming causation?"));
        return List.copyOf(result);
    }

    private record Eligible(CohortAggregateV1 cohort, OptionStatisticV1 stat) {
    }
}
