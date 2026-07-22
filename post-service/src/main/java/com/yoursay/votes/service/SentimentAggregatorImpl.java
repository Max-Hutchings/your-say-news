package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.SentimentAggregator;
import com.yoursay.votes.SentimentBreakdownDto;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.votes.error.VoteApiException;
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

    @Inject
    PostVotingConfigurationService votingConfigurationService;

    /** {@code k}-anonymity threshold. MVP1 default {@code 0} = no suppression (see roadmap risk #1). */
    @ConfigProperty(name = "votes.aggregation.suppress-below", defaultValue = "0")
    int suppressBelow;

    @Override
    public SentimentBreakdownDto overallSentiment(Long postId) {
        PostVotingConfigurationDto config = configuration(postId);
        return tally.overall(postId, config.votingType(), config.options(), votesForPost(postId));
    }

    @Override
    public SentimentBreakdownDto sentimentByCharacteristic(Long postId, String characteristic) {
        PostVotingConfigurationDto config = configuration(postId);
        return tally.byCharacteristic(postId, config.votingType(), config.options(), characteristic,
                votesForPost(postId), suppressBelow);
    }

    private List<VoteSnapshot> votesForPost(Long postId) {
        return voteRepository.listByPost(postId)
                .stream().map(SentimentAggregatorImpl::toVoteSnapshot).toList();
    }

    private static VoteSnapshot toVoteSnapshot(Vote vote) {
        CharacteristicSnapshot snapshot = vote.getSnapshot();
        return new VoteSnapshot(vote.getOptionId(), snapshot != null ? snapshot : CharacteristicSnapshot.empty());
    }

    private PostVotingConfigurationDto configuration(Long postId) {
        return votingConfigurationService.findByPostId(postId)
                .orElseThrow(() -> VoteApiException.postMissing(postId));
    }
}
