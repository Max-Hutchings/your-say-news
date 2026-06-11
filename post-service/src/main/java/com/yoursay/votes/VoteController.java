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
    VoteService voteService;

    @GET
    @Path("/{postId}")
    public Uni<List<VoteDto>> getPostVotes(@PathParam("postId") Long postId) {
        Log.infof("Endpoint Called: Get Post Votes for Post Id: %s", postId);
        return voteService.getPostVotes(postId);
    }


    @POST
    public Uni<VoteDto> postVote(VoteDto vote) {
        Log.infof("Endpoint Called: Post Vote for Vote: %s", vote);
        return voteService.save(vote);
    }




}
