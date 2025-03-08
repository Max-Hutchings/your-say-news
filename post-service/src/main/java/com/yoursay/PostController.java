package com.yoursay;

import com.yoursay.model.Post;
import com.yoursay.model.PostRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.util.List;

@Path("/posts")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PostController {

    @Inject
    PostRepository postRepository;

    @RestClient
    UserServiceClient userServiceClient;


    @POST
    public Uni<Post> savePost(Post post) {
        Log.infof("Endpoint Called: savePost - %s", post.getTitle());
        return userServiceClient.getUser(post.getUserId())
                .onFailure().invoke(e -> Log.errorf("User did not exist: %d", post.getUserId()))
                .flatMap(response -> {
                    if (response.getStatus() == 204) {
                        return Uni.createFrom().nullItem();
                    }
                     return postRepository.savePost(post);
                }).onFailure().invoke(e -> Log.errorf("Failed to save post: %s. Exception: %s", post.getId(), e));
    }


    @GET
    @Path("/{id}")
    public Uni<Post> getPost(@PathParam("id") Long id) {
        return postRepository.getPostById(id).onFailure().invoke(e -> Log.errorf("Failed to find post with id: %d. Exception: %s", id, e));
    }


    @GET
    @Path("/user/{userId}")
    public Uni<List<Post>> getUserPosts(@PathParam("userId") Long userId) {
        Log.infof("Endpoint Called: getUserPosts - %s", userId);
        return userServiceClient.getUser(userId)
                .onFailure().invoke(e -> Log.errorf("An error occurred when finding the user: %d. Exception: %s", userId, e))
                .flatMap(response -> {
                    if (response.getStatus() == 204) {
                        return Uni.createFrom().nullItem();
                    }
                    return postRepository.getPostsByUser(userId);
                }).onFailure().invoke(e -> Log.errorf("Failed to get posts for user: %d. Exception: %s", userId, e));
    }


}
