package com.yoursay.votes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.PostAnalysisAggregateService;
import com.yoursay.votes.PostAnalysisAggregateV1;
import com.yoursay.votes.error.VoteApiException;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@ApplicationScoped
public class PostAnalysisAggregateServiceImpl implements PostAnalysisAggregateService {
    @Inject
    VoteRepository voteRepository;

    @Inject
    PostVotingConfigurationService postService;

    @Inject
    PostAnalysisAggregateBuilder builder;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "votes.aggregation.suppress-below", defaultValue = "0")
    int suppressBelow;

    @Override
    @Transactional
    public PostAnalysisAggregateV1 capture(Long postId) {
        entityManager.createNativeQuery("set transaction isolation level repeatable read")
                .executeUpdate();
        PostVotingConfigurationDto post = postService.findByPostId(postId)
                .orElseThrow(() -> VoteApiException.postMissing(postId));
        List<VoteSnapshot> votes = voteRepository.listByPost(postId).stream()
                .map(PostAnalysisAggregateServiceImpl::snapshot)
                .toList();
        PostAnalysisAggregateV1 aggregate = builder.build(post, votes, suppressBelow, Instant.now());
        String version = aggregateVersion(aggregate);
        return new PostAnalysisAggregateV1(aggregate.schemaVersion(), aggregate.postId(),
                aggregate.votingType(), aggregate.summary(), aggregate.question(), aggregate.jurisdiction(),
                aggregate.options(), aggregate.canonicalVoteCount(), version, aggregate.capturedAt(),
                aggregate.overall(), aggregate.cohorts(), aggregate.metadata());
    }

    String aggregateVersion(PostAnalysisAggregateV1 aggregate) {
        return "sha256:" + sha256(versionPayload(aggregate));
    }

    private static PostAnalysisAggregateV1 versionPayload(PostAnalysisAggregateV1 aggregate) {
        return new PostAnalysisAggregateV1(aggregate.schemaVersion(), aggregate.postId(),
                aggregate.votingType(), aggregate.summary(), aggregate.question(),
                aggregate.jurisdiction(), aggregate.options(), aggregate.canonicalVoteCount(),
                null, null, aggregate.overall(), aggregate.cohorts(), aggregate.metadata());
    }

    private static VoteSnapshot snapshot(Vote vote) {
        CharacteristicSnapshot characteristics = vote.getSnapshot();
        return new VoteSnapshot(vote.getOptionId(),
                characteristics == null ? CharacteristicSnapshot.empty() : characteristics);
    }

    String sha256(PostAnalysisAggregateV1 aggregate) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(aggregate);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception e) {
            throw new IllegalStateException("Could not version Post Unwrapped aggregate", e);
        }
    }
}
