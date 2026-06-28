package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.SentimentAggregator;
import com.yoursay.votes.SentimentBreakdownDto;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Default {@link SentimentAggregator}: fetches a post's votes, reduces each to an anonymised
 * {@link VoteSnapshot}, and delegates the maths to {@link SentimentTally}. The {@code k}-suppression
 * threshold is injected from config so hardening against small-bucket re-identification is a single
 * property flip, no code change.
 *
 * <p>All methods are blocking and run on virtual threads via the JDBC datasource.
 * Per-vote {@link CharacteristicSnapshot} snapshots are now read from the vote row itself
 * (written at cast time by {@code VoteServiceImpl}).
 */
@ApplicationScoped
public class SentimentAggregatorImpl implements SentimentAggregator {

    @Inject
    VoteRepository voteRepository;

    @Inject
    SentimentTally tally;

    /** {@code k}-anonymity threshold. MVP1 default {@code 0} = no suppression (see roadmap risk #1). */
    @ConfigProperty(name = "votes.aggregation.suppress-below", defaultValue = "0")
    int suppressBelow;

    @Override
    public SentimentBreakdownDto overallSentiment(Long postId) {
        return tally.overall(postId, votesForPost(postId));
    }

    @Override
    public SentimentBreakdownDto sentimentByCharacteristic(Long postId, String characteristic) {
        return tally.byCharacteristic(postId, characteristic, votesForPost(postId), suppressBelow);
    }

    private List<VoteSnapshot> votesForPost(Long postId) {
        return voteRepository.listByPost(postId)
                .stream().map(SentimentAggregatorImpl::toVoteSnapshot).toList();
    }

    private static VoteSnapshot toVoteSnapshot(Vote vote) {
        CharacteristicSnapshot snapshot = vote.getSnapshot();
        return new VoteSnapshot(vote.isVoteFor(), snapshot != null ? snapshot : CharacteristicSnapshot.empty());
    }
}
