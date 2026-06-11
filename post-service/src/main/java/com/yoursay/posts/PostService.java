package com.yoursay.posts;

import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Public contract for the post domain.
 */
public interface PostService {

    Uni<PostDto> save(PostDto post);

    Uni<PostDto> getById(Long id);

    Uni<List<PostDto>> getByUser(Long userId);
}
