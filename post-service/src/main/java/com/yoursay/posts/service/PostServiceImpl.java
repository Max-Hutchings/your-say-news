package com.yoursay.posts.service;

import com.yoursay.posts.PostDto;
import com.yoursay.posts.PostService;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.posts.model.Post;
import com.yoursay.posts.model.PostRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class PostServiceImpl implements PostService {

    @Inject
    PostRepository postRepository;

    @RestClient
    UserServiceClient userServiceClient;

    @Override
    @WithTransaction
    public Uni<PostDto> save(PostDto post) {
        Log.infof("Saving post - %s", post.title());
        return userServiceClient.getUser(post.userId())
                .onFailure().invoke(e -> Log.errorf("User did not exist: %d", post.userId()))
                .flatMap(response -> {
                    if (response.getStatus() == 204) {
                        return Uni.createFrom().nullItem();
                    }
                    return postRepository.savePost(toEntity(post))
                            .map(PostServiceImpl::toDto)
                            .onItem().invoke(savedPost -> Log.infof(savedPost.toString()));
                }).onFailure().invoke(e -> Log.errorf("Failed to save post: %s. Exception: %s", post.id(), e));
    }

    @Override
    @WithSession
    public Uni<PostDto> getById(Long id) {
        return postRepository.getPostById(id)
                .map(PostServiceImpl::toDto)
                .onFailure().invoke(e -> Log.errorf("Failed to find post with id: %d. Exception: %s", id, e));
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> getByUser(Long userId) {
        Log.infof("Getting posts for user - %s", userId);
        return userServiceClient.getUser(userId)
                .onFailure().invoke(e -> Log.errorf("An error occurred when finding the user: %d. Exception: %s", userId, e))
                .flatMap(response -> {
                    if (response.getStatus() == 204) {
                        return Uni.createFrom().nullItem();
                    }
                    return postRepository.getPostsByUser(userId)
                            .map(posts -> posts.stream().map(PostServiceImpl::toDto).toList());
                }).onFailure().invoke(e -> Log.errorf("Failed to get posts for user: %d. Exception: %s", userId, e));
    }

    private static Post toEntity(PostDto post) {
        return new Post(
                post.id(),
                post.userId(),
                post.title(),
                post.description(),
                post.postedDate(),
                post.imageUrl()
        );
    }

    private static PostDto toDto(Post post) {
        if (post == null) {
            return null;
        }
        return new PostDto(
                post.getId(),
                post.getUserId(),
                post.getTitle(),
                post.getDescription(),
                post.getPostedDate(),
                post.getImageUrl()
        );
    }
}
