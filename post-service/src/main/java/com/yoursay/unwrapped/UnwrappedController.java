package com.yoursay.unwrapped;

import com.yoursay.unwrapped.dto.FollowUpRequest;

import com.yoursay.unwrapped.dto.UnwrappedResponseDto;

import com.yoursay.unwrapped.dto.FollowUpResponseDto;

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
import org.jboss.resteasy.reactive.ResponseStatus;

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
    @ResponseStatus(201)
    public FollowUpResponseDto followUp(@PathParam("postId") Long postId,
                                        @PathParam("storyId") UUID storyId,
                                        @Valid @NotNull FollowUpRequest request,
                                        @HeaderParam("Authorization") String authorization) {
        return service.followUp(postId, storyId, request.optionId(),
                identity.getPrincipal().getName(), authorization);
    }
}
