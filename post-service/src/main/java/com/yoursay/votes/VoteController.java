package com.yoursay.votes;

import com.yoursay.votes.error.VoteApiException;
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
    SentimentAggregator sentimentAggregator;

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Cast a vote on a post's support question. Returns 201 with the vote id and stance.
     * The voter's identity is taken from the JWT — never from the request body.
     */
    @POST
    public Response castVote(VoteRequestDto request,
                             @HeaderParam("Authorization") String authorization) {
        Long postId = request == null ? null : request.postId();
        Long optionId = request == null ? null : request.optionId();
        String email = securityIdentity.getPrincipal().getName();
        Log.infof("Casting canonical vote for postId=%s", postId);
        VoteResponseDto dto = voteService.castVote(postId, optionId, email, authorization);
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

    /**
     * Overall yes/no sentiment for a post's support question — the aggregate results unlocked after
     * voting. 404 if the post does not exist, 403 if the caller has not yet voted on it.
     */
    @GET
    @Path("/{postId}/sentiment")
    public SentimentBreakdownDto overallSentiment(@PathParam("postId") Long postId,
                                                  @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        voteService.assertResultsUnlocked(postId, email, authorization);
        return sentimentAggregator.overallSentiment(postId);
    }

    /**
     * Sentiment for a post broken down by one characteristic axis (a {@link CharacteristicSnapshot}
     * field name). Same gating as the overall result, plus a 400 if {@code axis} is not a real
     * breakdown axis — so an unknown axis is rejected rather than returning one misleading
     * all-{@code UNKNOWN} bucket.
     */
    @GET
    @Path("/{postId}/sentiment/{axis}")
    public SentimentBreakdownDto sentimentByCharacteristic(@PathParam("postId") Long postId,
                                                           @PathParam("axis") String axis,
                                                           @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        voteService.assertResultsUnlocked(postId, email, authorization);
        if (!CharacteristicSnapshot.isAxis(axis)) {
            throw VoteApiException.unknownAxis(axis);
        }
        return sentimentAggregator.sentimentByCharacteristic(postId, axis);
    }
}
