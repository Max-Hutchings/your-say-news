package com.yoursay.votes;

import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.service.SentimentTally;
import com.yoursay.votes.service.VoteSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OptionSentimentTallyTest {

    private final SentimentTally tally = new SentimentTally();
    private final List<VoteOptionDto> options = List.of(
            new VoteOptionDto(103L, "More frequent buses", 0, null),
            new VoteOptionDto(104L, "Protected cycle lanes", 1, null),
            new VoteOptionDto(105L, "Lower parking charges", 2, null));

    @Test
    void binaryDistributionUsesTheSameOptionAwareContract() {
        List<VoteOptionDto> binary = List.of(
                new VoteOptionDto(11L, "Agree", 0, "AGREE"),
                new VoteOptionDto(12L, "Disagree", 1, "DISAGREE"));

        SentimentBreakdownDto result = tally.overall(7L, VotingType.BINARY, binary, List.of(
                vote(11L, "LEFT"), vote(12L, "RIGHT"), vote(11L, "CENTRE"), vote(11L, "LEFT")));

        assertEquals(VotingType.BINARY, result.votingType());
        assertEquals(binary, result.options());
        assertEquals(List.of(
                        new ChoiceSentiment(11L, 3, 75.0),
                        new ChoiceSentiment(12L, 1, 25.0)),
                result.buckets().getFirst().choices());
    }

    @Test
    void overallReturnsExactCountsAndOmitsGloballyUnusedOptions() {
        SentimentBreakdownDto result = tally.overall(42L, VotingType.MULTIPLE_CHOICE, options, List.of(
                vote(104L, "LEFT"), vote(103L, "RIGHT"), vote(104L, "LEFT"),
                vote(103L, "LEFT"), vote(104L, "RIGHT")));

        assertEquals(VotingType.MULTIPLE_CHOICE, result.votingType());
        assertEquals(List.of(103L, 104L), result.options().stream().map(VoteOptionDto::id).toList());
        assertEquals(1, result.buckets().size());
        BucketSentiment overall = result.buckets().getFirst();
        assertEquals("OVERALL", overall.bucket());
        assertEquals(5, overall.total());
        assertEquals(new ChoiceSentiment(103L, 2, 40.0), overall.choices().get(0));
        assertEquals(new ChoiceSentiment(104L, 3, 60.0), overall.choices().get(1));
    }

    @Test
    void characteristicBucketsRetainAnActiveOptionAtZeroAndApplySuppression() {
        SentimentBreakdownDto result = tally.byCharacteristic(
                42L,
                VotingType.MULTIPLE_CHOICE,
                options,
                "politicalPersuasion",
                List.of(
                        vote(103L, "LEFT"), vote(103L, "LEFT"), vote(104L, "LEFT"),
                        vote(104L, "RIGHT"), vote(104L, "RIGHT")),
                3);

        assertEquals(1, result.suppressedBuckets());
        assertEquals(1, result.buckets().size());
        BucketSentiment left = result.buckets().getFirst();
        assertEquals("LEFT", left.bucket());
        assertEquals(3, left.total());
        assertEquals(new ChoiceSentiment(103L, 2, 100.0 * 2 / 3), left.choices().get(0));
        assertEquals(new ChoiceSentiment(104L, 1, 100.0 / 3), left.choices().get(1));
    }

    @Test
    void characteristicBucketIncludesZeroForAnOptionUsedElsewhere() {
        SentimentBreakdownDto result = tally.byCharacteristic(
                42L,
                VotingType.MULTIPLE_CHOICE,
                options,
                "politicalPersuasion",
                List.of(vote(103L, "LEFT"), vote(103L, "LEFT"), vote(104L, "RIGHT")),
                0);

        BucketSentiment left = result.buckets().stream()
                .filter(bucket -> bucket.bucket().equals("LEFT"))
                .findFirst().orElseThrow();
        assertEquals(new ChoiceSentiment(104L, 0, 0.0), left.choices().get(1));
    }

    @Test
    void emptyVoteSetReturnsNoOptionsAndNoBuckets() {
        SentimentBreakdownDto result = tally.overall(
                42L, VotingType.MULTIPLE_CHOICE, options, List.of());

        assertEquals(List.of(), result.options());
        assertEquals(List.of(), result.buckets());
        assertEquals(0, result.suppressedBuckets());
    }

    @Test
    void fiveOptionDistributionKeepsStableOrdinalOrderAndExactTotals() {
        List<VoteOptionDto> five = List.of(
                new VoteOptionDto(201L, "Keep the current fee", 0, null),
                new VoteOptionDto(202L, "Reduce it by 25%", 1, null),
                new VoteOptionDto(203L, "Reduce it by 50%", 2, null),
                new VoteOptionDto(204L, "Price by delivery cost", 3, null),
                new VoteOptionDto(205L, "Make core courses free", 4, null));
        SentimentBreakdownDto result = tally.overall(84L, VotingType.MULTIPLE_CHOICE, five, List.of(
                vote(205L, "LEFT"), vote(202L, "LEFT"), vote(205L, "RIGHT"),
                vote(201L, "RIGHT"), vote(204L, "CENTRE"), vote(203L, "CENTRE")));

        assertEquals(List.of(201L, 202L, 203L, 204L, 205L),
                result.options().stream().map(VoteOptionDto::id).toList());
        assertEquals(6, result.buckets().getFirst().total());
        assertEquals(List.of(1L, 1L, 1L, 1L, 2L),
                result.buckets().getFirst().choices().stream().map(ChoiceSentiment::count).toList());
    }

    private static VoteSnapshot vote(long optionId, String politics) {
        CharacteristicSnapshot empty = CharacteristicSnapshot.empty();
        return new VoteSnapshot(optionId, new CharacteristicSnapshot(
                politics, empty.ageRange(), empty.gender(), empty.sexAtBirth(), empty.sexualOrientation(),
                empty.maritalStatus(), empty.race(), empty.country(), empty.region(), empty.urbanRural(),
                empty.ukCounty(), empty.countryOfBirth(), empty.citizenship(), empty.religion(),
                empty.religiosity(), empty.education(), empty.occupation(), empty.employmentSector(),
                empty.universitySubject(), empty.personalIncomeRange(), empty.householdIncomeRange(),
                empty.height(), empty.weightRange(), empty.eyeColor(), empty.parent(), empty.newsFrequency(),
                empty.hasPet(), empty.petType(), empty.chronotype(), empty.outlook(), empty.neurodivergent(),
                empty.neurodivergenceType(), empty.hasDisability(), empty.disabilityType(), empty.housingStatus(),
                empty.propertyType()));
    }
}
