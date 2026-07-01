package com.yoursay.votes;

import com.yoursay.votes.service.SentimentTally;
import com.yoursay.votes.service.VoteSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the privacy aggregation engine — the single most important contract — produces correct
 * counts, percentages and {@code k}-suppression. Pure logic, no DB, so it pins exact expected values
 * and would fail loudly if the grouping or maths regressed.
 */
class SentimentTallyTest {

    private final SentimentTally tally = new SentimentTally();

    /** Snapshot with only the political axis set; everything else unknown. */
    private static CharacteristicSnapshot political(String leaning) {
        return new CharacteristicSnapshot(
                leaning, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Six votes: LEFT (yes,yes,no), RIGHT (no,no), and one voter with no political value (yes).
     * Used across the cases below so the expected splits are easy to verify by eye.
     */
    private static List<VoteSnapshot> sampleVotes() {
        return List.of(
                new VoteSnapshot(true, political("LEFT")),
                new VoteSnapshot(true, political("LEFT")),
                new VoteSnapshot(false, political("LEFT")),
                new VoteSnapshot(false, political("RIGHT")),
                new VoteSnapshot(false, political("RIGHT")),
                new VoteSnapshot(true, political(null)) // -> UNKNOWN bucket
        );
    }

    @Test
    void overall_talliesEveryVoteRegardlessOfCharacteristic() {
        SentimentBreakdownDto result = tally.overall(42L, sampleVotes());

        assertEquals(42L, result.postId());
        assertEquals(SentimentBreakdownDto.OVERALL, result.characteristic());
        assertEquals(0, result.suppressedBuckets());
        assertEquals(1, result.buckets().size());

        BucketSentiment overall = result.buckets().get(0);
        assertEquals(SentimentBreakdownDto.OVERALL, overall.bucket());
        assertEquals(3, overall.yesCount()); // 2 LEFT + 1 UNKNOWN
        assertEquals(3, overall.noCount());  // 1 LEFT + 2 RIGHT
        assertEquals(6, overall.total());
        assertEquals(50.0, overall.yesPct(), 0.001);
        assertEquals(50.0, overall.noPct(), 0.001);
    }

    @Test
    void byCharacteristic_groupsAndComputesPercentages_largestBucketFirst() {
        SentimentBreakdownDto result =
                tally.byCharacteristic(42L, "politicalPersuasion", sampleVotes(), 0);

        assertEquals("politicalPersuasion", result.characteristic());
        assertEquals(0, result.suppressedBuckets());
        assertEquals(3, result.buckets().size());

        // Sorted by total descending: LEFT(3) > RIGHT(2) > UNKNOWN(1).
        BucketSentiment left = result.buckets().get(0);
        assertEquals("LEFT", left.bucket());
        assertEquals(2, left.yesCount());
        assertEquals(1, left.noCount());
        assertEquals(3, left.total());
        assertEquals(66.667, left.yesPct(), 0.001);
        assertEquals(33.333, left.noPct(), 0.001);

        BucketSentiment right = result.buckets().get(1);
        assertEquals("RIGHT", right.bucket());
        assertEquals(0, right.yesCount());
        assertEquals(2, right.noCount());
        assertEquals(0.0, right.yesPct(), 0.001);
        assertEquals(100.0, right.noPct(), 0.001);

        BucketSentiment unknown = result.buckets().get(2);
        assertEquals(CharacteristicSnapshot.UNKNOWN, unknown.bucket());
        assertEquals(1, unknown.total());
    }

    @Test
    void byCharacteristic_ordersBucketsByTotalDescending_regardlessOfInsertionOrder() {
        // Smallest bucket is seen FIRST, so first-seen order (RIGHT, LEFT) is the opposite of the
        // size order (LEFT 3 > RIGHT 1). Proves results are sorted by total, not insertion order.
        List<VoteSnapshot> votes = List.of(
                new VoteSnapshot(false, political("RIGHT")),
                new VoteSnapshot(true, political("LEFT")),
                new VoteSnapshot(true, political("LEFT")),
                new VoteSnapshot(false, political("LEFT"))
        );

        SentimentBreakdownDto result = tally.byCharacteristic(7L, "politicalPersuasion", votes, 0);

        assertEquals(List.of("LEFT", "RIGHT"),
                result.buckets().stream().map(BucketSentiment::bucket).toList());
        assertEquals(3, result.buckets().get(0).total());
        assertEquals(1, result.buckets().get(1).total());
    }

    @Test
    void byCharacteristic_suppressesBucketsBelowK_andCountsThem() {
        // k=2 withholds UNKNOWN (total 1); k=3 also withholds RIGHT (total 2).
        SentimentBreakdownDto k2 =
                tally.byCharacteristic(42L, "politicalPersuasion", sampleVotes(), 2);
        assertEquals(1, k2.suppressedBuckets());
        assertEquals(List.of("LEFT", "RIGHT"), k2.buckets().stream().map(BucketSentiment::bucket).toList());

        SentimentBreakdownDto k3 =
                tally.byCharacteristic(42L, "politicalPersuasion", sampleVotes(), 3);
        assertEquals(2, k3.suppressedBuckets());
        assertEquals(List.of("LEFT"), k3.buckets().stream().map(BucketSentiment::bucket).toList());
    }

    @Test
    void byCharacteristic_defaultKZero_suppressesNothing() {
        SentimentBreakdownDto result =
                tally.byCharacteristic(42L, "politicalPersuasion", sampleVotes(), 0);
        assertEquals(0, result.suppressedBuckets());
        assertEquals(3, result.buckets().size());
    }
}
