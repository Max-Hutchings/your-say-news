package com.yoursay.posts;

import com.yoursay.posts.dto.PresignResponse;

import com.yoursay.posts.dto.PresignRequest;

import com.yoursay.posts.dto.CreatePostRequest;

import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.dto.PostCreationProvenance;
import com.yoursay.posts.dto.PostSourceDto;

import com.yoursay.posts.postagent.AgentService;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

@Path("/posts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
public class PostController {

    @Inject
    PostService postService;

    @Inject
    AgentService agentService;

    @Inject
    SecurityIdentity securityIdentity;

    /** Mint a presigned S3 PUT URL for a media upload. */
    @POST
    @Path("/media/presign")
    public Uni<PresignResponse> presign(@Valid @NotNull PresignRequest request,
                                        @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        Log.infof("Endpoint Called: presign - %s %s", request.mediaType(), request.contentType());
        return postService.presignUpload(email, authorization, request);
    }

    /** Create a post. Author is taken from the token, never the body. */
    @POST
    @ResponseStatus(201)
    public Uni<PostDto> createPost(@Valid @NotNull CreatePostRequest request,
                                   @HeaderParam("Authorization") String authorization) {
        String email = securityIdentity.getPrincipal().getName();
        if (request.pepperDraftId() == null) {
            return postService.create(email, authorization, request, null);
        }
        Context eventLoop = Vertx.currentContext();
        return Uni.createFrom().item(() -> provenance(request, authorization))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .emitOn(command -> eventLoop.runOnContext(ignored -> command.run()))
                .flatMap(provenance -> postService.create(email, authorization, request, provenance)
                        .flatMap(post -> Uni.createFrom().item(() -> {
                                    agentService.markPublished(provenance.pepperDraftId(), post.id());
                                    return post;
                                })
                                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())));
    }

    private PostCreationProvenance provenance(
            CreatePostRequest request, String authorization) {
        if (request.pepperDraftId() == null) {
            return null;
        }
        List<AgentSourceDto> selected = request.citations() == null
                ? List.of()
                : request.citations().stream()
                        .map(source -> new AgentSourceDto(
                                source.url(), source.title(), source.publisher()))
                        .toList();
        AgentPublicationDto verified = agentService.preparePublication(
                request.pepperDraftId(), selected, authorization);
        return new PostCreationProvenance(
                verified.draftId(),
                verified.sources().stream()
                        .map(source -> new PostSourceDto(
                                source.url(), source.title(), source.publisher()))
                        .toList());
    }

    /** Get a post by id; 204 if it does not exist. */
    @GET
    @Path("/{id}")
    public Uni<RestResponse<PostDto>> getPost(@PathParam("id") Long id) {
        return postService.getById(id)
                .map(post -> post == null
                        ? RestResponse.noContent()
                        : RestResponse.ok(post));
    }

    /** Posts by a given author, newest first. */
    @GET
    @Path("/user/{userId}")
    public Uni<List<PostDto>> getUserPosts(@PathParam("userId") Long userId) {
        Log.infof("Endpoint Called: getUserPosts - %s", userId);
        return postService.getByUser(userId);
    }

    /**
     * A page of recent posts across all authors, newest first (interim feed). The feed loads a
     * page at a time and requests the next as the reader nears the end. {@code size} is capped
     * server-side.
     */
    @GET
    public Uni<List<PostDto>> getRecentPosts(@QueryParam("page") @DefaultValue("0") int page,
                                             @QueryParam("size") @DefaultValue("5") int size) {
        Log.infof("Endpoint Called: getRecentPosts - page %d size %d", page, size);
        return postService.getRecent(page, size);
    }
}
