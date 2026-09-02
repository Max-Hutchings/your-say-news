package com.yoursay.unwrapped;

import com.yoursay.unwrapped.dto.RejectStoryRequest;
import com.yoursay.unwrapped.dto.ReviewStoryDto;
import com.yoursay.unwrapped.dto.UnwrappedAdminPostDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationTriggerDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationMonitorDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkPromptDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkRequest;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkResponseDto;

import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;
import java.util.UUID;

@Path("/api/admin/unwrapped")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
public class UnwrappedAdminController {
    @Inject
    UnwrappedService service;
    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/review")
    @RunOnVirtualThread
    public List<ReviewStoryDto> reviewQueue() {
        return service.reviewQueue();
    }

    @GET
    @Path("/posts")
    @NonBlocking
    public Uni<List<UnwrappedAdminPostDto>> analysisPosts(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size) {
        return service.analysisPosts(page, size);
    }

    @GET
    @Path("/generation-status")
    @RunOnVirtualThread
    public UnwrappedGenerationMonitorDto generationStatus() {
        return service.generationMonitor();
    }

    @GET
    @Path("/{storyId}")
    @RunOnVirtualThread
    public ReviewStoryDto story(@PathParam("storyId") UUID storyId) {
        return service.reviewStory(storyId);
    }

    @POST
    @Path("/posts/{postId}/generate")
    @Consumes(MediaType.WILDCARD)
    @ResponseStatus(202)
    @RunOnVirtualThread
    public UnwrappedGenerationTriggerDto triggerGeneration(@PathParam("postId") Long postId) {
        return service.triggerGeneration(postId);
    }

    @GET
    @Path("/posts/{postId}/benchmark/context")
    @RunOnVirtualThread
    public UnwrappedBenchmarkPromptDto benchmarkPrompt(@PathParam("postId") Long postId) {
        return service.benchmarkPrompt(postId);
    }

    @POST
    @Path("/posts/{postId}/benchmark")
    @RunOnVirtualThread
    public UnwrappedBenchmarkResponseDto generateBenchmark(
            @PathParam("postId") Long postId,
            @Valid @NotNull UnwrappedBenchmarkRequest request
    ) {
        return service.generateBenchmark(postId, request.systemPrompts());
    }

    @POST
    @Path("/{storyId}/approve")
    @RunOnVirtualThread
    public ReviewStoryDto approve(@PathParam("storyId") UUID storyId) {
        return service.approve(storyId, identity.getPrincipal().getName());
    }

    @POST
    @Path("/{storyId}/reject")
    @RunOnVirtualThread
    public ReviewStoryDto reject(@PathParam("storyId") UUID storyId,
                                 @Valid @NotNull RejectStoryRequest request) {
        return service.reject(storyId, identity.getPrincipal().getName(), request.reason());
    }
}
