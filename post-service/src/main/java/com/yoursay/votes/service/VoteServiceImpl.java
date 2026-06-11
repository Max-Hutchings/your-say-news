package com.yoursay.votes.service;

import com.yoursay.votes.VoteDto;
import com.yoursay.votes.VoteService;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class VoteServiceImpl implements VoteService {

    @Inject
    VoteRepository voteRepository;

    @Override
    @WithSession
    public Uni<List<VoteDto>> getPostVotes(Long postId) {
        return voteRepository.getPostVotes(postId)
                .map(votes -> votes.stream().map(VoteServiceImpl::toDto).toList())
                .onFailure().invoke(e -> Log.errorf("Error getting Post Votes for Post Id: %s. Exception: %s", postId, e));
    }

    @Override
    @WithTransaction
    public Uni<VoteDto> save(VoteDto vote) {
        return voteRepository.saveVote(toEntity(vote))
                .map(VoteServiceImpl::toDto)
                .onFailure().invoke(e -> Log.errorf("Failed to save vote: %d. Exception: %s", vote.id(), e));
    }

    private static Vote toEntity(VoteDto vote) {
        return new Vote(vote.id(), vote.postId(), vote.voteFor(), vote.userId());
    }

    private static VoteDto toDto(Vote vote) {
        if (vote == null) {
            return null;
        }
        return new VoteDto(vote.getId(), vote.getPostId(), vote.isVoteFor(), vote.getUserId());
    }
}
