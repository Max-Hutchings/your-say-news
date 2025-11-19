package com.yoursay.votes;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

import java.util.List;

@Path("/vote")
public class VoteController {

    @Inject
    VoteRepository voteRepository;

    @GET
    @Path("/{postId}")
    public Uni<List<Vote>> getPostVotes(@PathParam("postId") Long postId) {
        Log.infof("Endpoint Called: Get Post Votes for Post Id: %s", postId);
        return voteRepository.getPostVotes(postId).onFailure().invoke(e -> Log.errorf("Error getting Post Votes for Post Id: %s. Exception: %s", postId, e));
    }


    @POST
    public Uni<Vote> postVote(Vote vote) {
        Log.infof("Endpoint Called: Post Vote for Vote: %s", vote);
        return voteRepository.saveVote(vote).onFailure().invoke(e -> Log.errorf("Failed to save vote: %d. Exception: %s", vote.getId(), e));
    }




}
