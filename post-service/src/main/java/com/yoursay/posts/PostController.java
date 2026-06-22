package com.yoursay.posts;

import io.quarkus.logging.Log;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
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
        Log.infof("Endpoint Called: createPost - %s by %s", request.title(), email);
        // Forward the caller's bearer so the role-gated user-service lookup authorises (resolves the
        // author id behind this email). The token only travels service-to-service, never onto the post.
        return postService.create(email, authorization, request);
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

    /** Recent posts across all authors, newest first (interim feed). */
    @GET
    public Uni<List<PostDto>> getRecentPosts() {
        Log.info("Endpoint Called: getRecentPosts");
        return postService.getRecent();
    }
}
