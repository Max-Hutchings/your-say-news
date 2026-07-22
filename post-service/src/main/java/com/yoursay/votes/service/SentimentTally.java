package com.yoursay.votes.service;

import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.BucketSentiment;
import com.yoursay.votes.ChoiceSentiment;
import com.yoursay.votes.SentimentBreakdownDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure, identity-free option tally used for both binary and multiple-choice posts. */
@ApplicationScoped
public class SentimentTally {

    public SentimentBreakdownDto overall(Long postId, VotingType votingType,
                                         List<VoteOptionDto> options, List<VoteSnapshot> votes) {
        List<VoteOptionDto> active = activeOptions(options, votes);
        if (votes.isEmpty()) {
            return new SentimentBreakdownDto(postId, votingType, SentimentBreakdownDto.OVERALL,
                    List.of(), List.of(), 0);
        }
        return new SentimentBreakdownDto(postId, votingType, SentimentBreakdownDto.OVERALL, active,
                List.of(bucket(SentimentBreakdownDto.OVERALL, active, votes)), 0);
    }

    public SentimentBreakdownDto byCharacteristic(Long postId, VotingType votingType,
                                                  List<VoteOptionDto> options, String axis,
                                                  List<VoteSnapshot> votes, int suppressBelow) {
        List<VoteOptionDto> active = activeOptions(options, votes);
        Map<String, List<VoteSnapshot>> grouped = new LinkedHashMap<>();
        for (VoteSnapshot vote : votes) {
            grouped.computeIfAbsent(vote.snapshot().bucketFor(axis), ignored -> new ArrayList<>()).add(vote);
        }
        List<BucketSentiment> surfaced = new ArrayList<>();
        long suppressed = 0;
        for (Map.Entry<String, List<VoteSnapshot>> entry : grouped.entrySet()) {
            if (entry.getValue().size() < suppressBelow) {
                suppressed++;
            } else {
                surfaced.add(bucket(entry.getKey(), active, entry.getValue()));
            }
        }
        surfaced.sort(Comparator.comparingLong(BucketSentiment::total).reversed());
        return new SentimentBreakdownDto(postId, votingType, axis, active, surfaced, suppressed);
    }

    private static List<VoteOptionDto> activeOptions(List<VoteOptionDto> options, List<VoteSnapshot> votes) {
        Set<Long> used = new LinkedHashSet<>();
        votes.forEach(vote -> used.add(vote.optionId()));
        return options.stream()
                .filter(option -> used.contains(option.id()))
                .sorted(Comparator.comparingInt(VoteOptionDto::ordinal))
                .toList();
    }

    private static BucketSentiment bucket(String label, List<VoteOptionDto> active,
                                           List<VoteSnapshot> votes) {
        long total = votes.size();
        Map<Long, Long> counts = new LinkedHashMap<>();
        votes.forEach(vote -> counts.merge(vote.optionId(), 1L, Long::sum));
        List<ChoiceSentiment> choices = active.stream().map(option -> {
            long count = counts.getOrDefault(option.id(), 0L);
            double percentage = total == 0 ? 0.0 : 100.0 * count / total;
            return new ChoiceSentiment(option.id(), count, percentage);
        }).toList();
        return new BucketSentiment(label, total, choices);
    }
}
