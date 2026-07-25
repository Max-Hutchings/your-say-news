package com.yoursay.unwrapped.selection;

import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.unwrapped.CandidateRole;
import com.yoursay.unwrapped.OptionBriefV1;
import com.yoursay.unwrapped.UnwrappedAnalysisBriefV1;
import com.yoursay.votes.AggregationMetadataV1;
import com.yoursay.votes.CohortAggregateV1;
import com.yoursay.votes.CohortDimensionV1;
import com.yoursay.votes.MembershipSemantics;
import com.yoursay.votes.OptionStatisticV1;
import com.yoursay.votes.OverallOptionStatisticV1;
import com.yoursay.votes.PostAnalysisAggregateV1;
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
                cohort("eyeColor=BLUE", "eyeColor", "BLUE", 90, 29, 0.001)
        ));

        UnwrappedAnalysisBriefV1 brief = selector.select(aggregate);
        OptionBriefV1 support = brief.options().getFirst();

        assertEquals(List.of("occupation=EMPLOYED", "ageRange=AGE_25_34",
                        "ageRange=AGE_25_34&gender=MAN"),
                support.candidates().stream().map(value -> value.cohortId()).toList());
        assertEquals(List.of(CandidateRole.CORE_ANCHOR, CandidateRole.CORE_DIFFERENTIATOR,
                        CandidateRole.INTERSECTION_DISCOVERY),
                support.candidates().stream().map(value -> value.role()).toList());
        assertNull(support.insufficientEvidence());
        assertEquals(List.of(
                "Do not claim that a demographic characteristic caused a vote.",
                "Do not claim this self-selected audience represents a jurisdiction's population.",
                "Do not introduce a cohort absent from this shortlist.",
                "Distinguish observed vote data, external context, and interpretation."),
                support.prohibitedInferences());
        OptionBriefV1 oppose = brief.options().get(1);
        assertEquals(125, oppose.overallVoteCount());
        assertEquals(50.0, oppose.overallVotePercentage());
        assertEquals(List.of(), oppose.candidates());
        assertEquals("No reliable demographic concentration passes the versioned narration rules.",
                oppose.insufficientEvidence());
    }

    @Test
    void rejectsDramaticSparseAndUncorrectedSignalsAndReturnsExplicitNoEvidence() {
        PostAnalysisAggregateV1 aggregate = aggregate(100, List.of(
                cohort("gender=MAN", "gender", "MAN", 29, 65, 0.001),
                cohort("ageRange=AGE_25_34", "ageRange", "AGE_25_34", 40, 30, 0.051),
                cohort("occupation=EMPLOYED", "occupation", "EMPLOYED", 70, 9.99, 0.001)
        ));

        OptionBriefV1 support = selector.select(aggregate).options().getFirst();

        assertEquals(List.of(), support.candidates());
        assertEquals("No reliable demographic concentration passes the versioned narration rules.",
                support.insufficientEvidence());
    }

    @Test
    void minimumOverallSampleIsAnInclusiveHundredVoteBoundary() {
        assertEquals(0, selector.select(aggregate(99, List.of(
                cohort("gender=MAN", "gender", "MAN", 60, 20, 0.001))))
                .options().getFirst().candidates().size());
        assertEquals(1, selector.select(aggregate(100, List.of(
                cohort("gender=MAN", "gender", "MAN", 60, 20, 0.001))))
                .options().getFirst().candidates().size());
    }

    @Test
    void narrationFloorsAreInclusiveForShareEffectQValueAndSingleOrIntersectionSamples() {
        assertEquals(1, selectedCount(cohort("gender=MAN", "gender", "MAN", 30, 10, 0.05)));
        assertEquals(0, selectedCount(cohort("gender=MAN", "gender", "MAN", 29, 10, 0.05)));
        assertEquals(0, selectedCount(cohort("gender=MAN", "gender", "MAN", 30, 9.99, 0.05)));
        assertEquals(0, selectedCount(cohort("gender=MAN", "gender", "MAN", 30, 10, 0.051)));
        assertEquals(1, selectedCount(intersection("ageRange=AGE_25_34&gender=MAN", 40, 10, 0.05)));
        assertEquals(0, selectedCount(intersection("ageRange=AGE_25_34&gender=MAN", 39, 10, 0.05)));

        CohortAggregateV1 belowShare = new CohortAggregateV1(
                "gender=MAN", List.of(new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.EXCLUSIVE, 40, 4.99, statistics(40, 10, 0.05));
        assertEquals(0, selectedCount(belowShare));
    }

    private int selectedCount(CohortAggregateV1 cohort) {
        return selector.select(aggregate(100, List.of(cohort)))
                .options().getFirst().candidates().size();
    }

    private static PostAnalysisAggregateV1 aggregate(long count, List<CohortAggregateV1> cohorts) {
        List<VoteOptionDto> options = List.of(
                new VoteOptionDto(101L, "Support", 0, null),
                new VoteOptionDto(102L, "Oppose", 1, null));
        return new PostAnalysisAggregateV1("post-analysis-aggregate-v1", 42L,
                VotingType.BINARY, "A factual policy summary.", "Should the policy change?", "GLOBAL", options, count,
                "sha256:fixture", Instant.parse("2026-07-25T12:00:00Z"),
                List.of(new OverallOptionStatisticV1(101L, count / 2, 50),
                        new OverallOptionStatisticV1(102L, count / 2, 50)),
                cohorts, new AggregationMetadataV1("cohort-rules-v1", 0, 100, 30, 40,
                5, 10, 0.05, 0, cohorts.size() * 2L));
    }

    private static CohortAggregateV1 cohort(String id, String axis, String bucket,
                                            long sample, double effect, double q) {
        return new CohortAggregateV1(id, List.of(new CohortDimensionV1(axis, bucket)),
                MembershipSemantics.EXCLUSIVE, sample, 20,
                statistics(sample, effect, q));
    }

    private static CohortAggregateV1 intersection(String id, long sample, double effect, double q) {
        return new CohortAggregateV1(id,
                List.of(new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("gender", "MAN")),
                MembershipSemantics.EXCLUSIVE, sample, 10, statistics(sample, effect, q));
    }

    private static List<OptionStatisticV1> statistics(long sample, double effect, double q) {
        return List.of(
                new OptionStatisticV1(101L, Math.round(sample * 0.7), 70, 25, 20, effect,
                        60, 78, 0.001, q, "TWO_PROPORTION_Z"),
                new OptionStatisticV1(102L, Math.round(sample * 0.3), 30, 10, -20, -effect,
                        22, 40, 0.001, q, "TWO_PROPORTION_Z"));
    }
}
