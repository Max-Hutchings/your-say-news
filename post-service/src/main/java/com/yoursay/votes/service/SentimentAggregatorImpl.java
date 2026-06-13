package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.SentimentAggregator;
import com.yoursay.votes.SentimentBreakdownDto;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
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
 * <p><strong>Stage 0 status:</strong> the contract, the engine and the OVERALL path are complete and
 * exercised. Per-vote {@link CharacteristicSnapshot} <em>persistence</em> lands in Stage 3; until
 * then {@link #toVoteSnapshot} attaches an empty snapshot, so {@code sentimentByCharacteristic}
 * currently returns a single {@code UNKNOWN} bucket. The grouping/percentage/suppression logic it
 * relies on is fully proven by {@code SentimentTallyTest}.
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
    @WithSession
    public Uni<SentimentBreakdownDto> overallSentiment(Long postId) {
        return votesForPost(postId).map(votes -> tally.overall(postId, votes));
    }

    @Override
    @WithSession
    public Uni<SentimentBreakdownDto> sentimentByCharacteristic(Long postId, String characteristic) {
        return votesForPost(postId)
                .map(votes -> tally.byCharacteristic(postId, characteristic, votes, suppressBelow));
    }

    private Uni<List<VoteSnapshot>> votesForPost(Long postId) {
        return voteRepository.getPostVotes(postId)
                .map(votes -> votes.stream().map(SentimentAggregatorImpl::toVoteSnapshot).toList());
    }

    private static VoteSnapshot toVoteSnapshot(Vote vote) {
        // Stage 3: read the snapshot persisted on the vote instead of CharacteristicSnapshot.empty().
        return new VoteSnapshot(vote.isVoteFor(), CharacteristicSnapshot.empty());
    }
}
