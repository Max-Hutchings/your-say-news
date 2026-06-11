package com.yoursay.posts;

import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/posts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PostController {

    @Inject
    PostService postService;


    @POST
    public Uni<PostDto> savePost(PostDto post) {
        Log.infof("Endpoint Called: savePost - %s", post.title());
        return postService.save(post);
    }


    @GET
    @Path("/{id}")
    public Uni<PostDto> getPost(@PathParam("id") Long id) {
        return postService.getById(id);
    }


    @GET
    @Path("/user/{userId}")
    public Uni<List<PostDto>> getUserPosts(@PathParam("userId") Long userId) {
        Log.infof("Endpoint Called: getUserPosts - %s", userId);
        return postService.getByUser(userId);
    }


}
