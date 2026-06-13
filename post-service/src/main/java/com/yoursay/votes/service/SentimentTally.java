package com.yoursay.votes.service;

import com.yoursay.votes.BucketSentiment;
import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.SentimentBreakdownDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pure aggregation engine — the single place the "sentiment by characteristic" maths lives, so
 * it can be got right once and tested in isolation. No database, no identity: it takes
 * {@link VoteSnapshot}s in and returns a {@link SentimentBreakdownDto} out.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>group votes into buckets by a chosen {@link CharacteristicSnapshot} axis (or one OVERALL
 *       bucket),</li>
 *   <li>tally yes/no counts and percentages per bucket,</li>
 *   <li>apply the {@code k}-anonymity threshold: withhold any bucket whose total is below {@code k}
 *       and report how many were withheld.</li>
 * </ul>
 */
@ApplicationScoped
public class SentimentTally {

    /** Two running counters for one bucket while we fold over the votes. */
    private static final class Counter {
        long yes;
        long no;

        void add(boolean voteFor) {
            if (voteFor) {
                yes++;
            } else {
                no++;
            }
        }
    }

    /**
     * Overall yes/no split as a single {@link SentimentBreakdownDto#OVERALL} bucket. The OVERALL
     * tally itself is never suppressed (it identifies no one); {@code suppressBelow} is irrelevant
     * here and the result simply reflects all votes.
     */
    public SentimentBreakdownDto overall(Long postId, List<VoteSnapshot> votes) {
        Counter counter = new Counter();
        for (VoteSnapshot vote : votes) {
            counter.add(vote.voteFor());
        }
        List<BucketSentiment> buckets = counter.yes + counter.no == 0
                ? List.of()
                : List.of(BucketSentiment.of(SentimentBreakdownDto.OVERALL, counter.yes, counter.no));
        return new SentimentBreakdownDto(postId, SentimentBreakdownDto.OVERALL, buckets, 0);
    }

    /**
     * Yes/no split grouped by one characteristic axis, with {@code k}-suppression applied.
     *
     * @param axis         a {@link CharacteristicSnapshot} field name (e.g. {@code "politicalPersuasion"})
     * @param suppressBelow the {@code k} threshold; buckets with {@code total < k} are withheld.
     *                      {@code 0} (the MVP1 default) suppresses nothing.
     */
    public SentimentBreakdownDto byCharacteristic(Long postId, String axis, List<VoteSnapshot> votes, int suppressBelow) {
        // LinkedHashMap: first-seen ordering is stable, so a fixed vote set yields a stable result.
        Map<String, Counter> byBucket = new LinkedHashMap<>();
        for (VoteSnapshot vote : votes) {
            String bucket = vote.snapshot().bucketFor(axis);
            byBucket.computeIfAbsent(bucket, b -> new Counter()).add(vote.voteFor());
        }

        List<BucketSentiment> surfaced = new ArrayList<>();
        long suppressed = 0;
        for (Map.Entry<String, Counter> entry : byBucket.entrySet()) {
            Counter c = entry.getValue();
            long total = c.yes + c.no;
            if (total < suppressBelow) {
                suppressed++;
                continue;
            }
            surfaced.add(BucketSentiment.of(entry.getKey(), c.yes, c.no));
        }

        // Largest buckets first — the most-populated slices read first in the results UI.
        surfaced.sort(Comparator.comparingLong(BucketSentiment::total).reversed());
        return new SentimentBreakdownDto(postId, axis, surfaced, suppressed);
    }
}
