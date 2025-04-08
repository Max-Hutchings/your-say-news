package com.yoursay.model;

import io.smallrye.mutiny.Uni;

import java.util.List;

public class VoteRepository{

    public Uni<List<Vote>> getPostVotes(Long postId){
        return Vote.list("postId", postId);
    }


    public Uni<Vote> saveVote(Vote vote){
        return vote.persist();
    }
}
