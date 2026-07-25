package com.yoursay.unwrapped;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/posts/{postId}/unwrapped")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@RunOnVirtualThread
public class UnwrappedController {
    @Inject
    UnwrappedService service;

    @Inject
    SecurityIdentity identity;

    @GET
    public UnwrappedResponseDto get(@PathParam("postId") Long postId,
                                    @HeaderParam("Authorization") String authorization) {
        return service.get(postId, identity.getPrincipal().getName(), authorization);
    }

    @POST
    @Path("/{storyId}/follow-up")
    public Response followUp(@PathParam("postId") Long postId,
                             @PathParam("storyId") UUID storyId,
                             @Valid @NotNull FollowUpRequest request,
                             @HeaderParam("Authorization") String authorization) {
        FollowUpResponseDto response = service.followUp(postId, storyId, request.optionId(),
                identity.getPrincipal().getName(), authorization);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }
}
