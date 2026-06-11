package com.yoursay.votes.model;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VoteRepository{

    public Uni<List<Vote>> getPostVotes(Long postId){
        return Vote.list("postId", postId);
    }


    public Uni<Vote> saveVote(Vote vote){
        return vote.persist();
    }
}
