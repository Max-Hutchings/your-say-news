package com.yoursay.votes;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/votes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class VoteController {

    @Inject
    VoteService voteService;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Cast a vote on a post's support question. Returns 201 with the vote id and stance.
     * The voter's identity is taken from the JWT — never from the request body.
     */
    @POST
    public Response castVote(VoteRequestDto request,
                             @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        Log.infof("castVote: postId=%d voteFor=%b caller=%s", request.postId(), request.voteFor(), email);
        VoteResponseDto dto = voteService.castVote(request.postId(), request.voteFor(), email, authorization);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    /**
     * The authenticated user's existing vote on a post. Returns 200 if found, 204 if they have
     * not yet voted.
     */
    @GET
    @Path("/{postId}/mine")
    public Response getMyVote(@PathParam("postId") Long postId,
                              @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        return voteService.getMyVote(postId, email, authorization)
                .map(dto -> Response.ok(dto).build())
                .orElse(Response.noContent().build());
    }

    /** Total votes cast on a post (any stance). */
    @GET
    @Path("/{postId}/count")
    public long countForPost(@PathParam("postId") Long postId) {
        return voteService.countForPost(postId);
    }
}
