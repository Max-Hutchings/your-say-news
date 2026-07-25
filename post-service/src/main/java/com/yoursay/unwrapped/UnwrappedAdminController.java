package com.yoursay.unwrapped;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/admin/unwrapped")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@RunOnVirtualThread
public class UnwrappedAdminController {
    @Inject
    UnwrappedService service;
    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/review")
    public List<ReviewStoryDto> reviewQueue() {
        return service.reviewQueue();
    }

    @GET
    @Path("/{storyId}")
    public ReviewStoryDto story(@PathParam("storyId") UUID storyId) {
        return service.reviewStory(storyId);
    }

    @POST
    @Path("/posts/{postId}/prediction")
    public Response prediction(@PathParam("postId") Long postId) {
        return Response.accepted(Map.of("jobId", service.enqueuePrediction(postId))).build();
    }

    @POST
    @Path("/{storyId}/approve")
    public ReviewStoryDto approve(@PathParam("storyId") UUID storyId) {
        return service.approve(storyId, identity.getPrincipal().getName());
    }

    @POST
    @Path("/{storyId}/reject")
    public ReviewStoryDto reject(@PathParam("storyId") UUID storyId,
                                 @Valid @NotNull RejectStoryRequest request) {
        return service.reject(storyId, identity.getPrincipal().getName(), request.reason());
    }
}
