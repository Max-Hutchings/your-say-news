package com.yoursay.posts;

import com.yoursay.posts.dto.PresignResponse;

import com.yoursay.posts.dto.PresignRequest;

import com.yoursay.posts.dto.CreatePostRequest;

import com.yoursay.posts.dto.PostDto;

import com.yoursay.posts.dto.PostPageRequest;
import com.yoursay.posts.dto.PostCreationProvenance;

import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Public contract for the post domain.
 */
public interface PostService {

    /** Presign an S3 PUT URL for a media upload owned by the authenticated user. */
    Uni<PresignResponse> presignUpload(String authorEmail, String authorization, PresignRequest request);

    /**
     * Create a post authored by the user behind {@code authorEmail}. The author id is resolved
     * server-side through the local user-domain adapter. {@code authorization} remains as a
     * compatibility parameter during the merge. AI provenance is server-derived.
     */
    Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request);

    /** Create through the same post path with verified Pepper provenance and selected sources. */
    default Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request,
                                PostCreationProvenance provenance) {
        return create(authorEmail, authorization, request);
    }

    /** Create for a publisher identity already verified by a trusted server-side workflow. */
    default Uni<PostDto> createForPublisher(Long publisherUserId, CreatePostRequest request,
                                            PostCreationProvenance provenance) {
        return Uni.createFrom().failure(
                new UnsupportedOperationException("Trusted publisher creation is not implemented"));
    }

    /** A single post with presigned media URLs, or null if it does not exist. */
    Uni<PostDto> getById(Long id);

    /** Posts by the given author, newest first. */
    Uni<List<PostDto>> getByUser(Long userId);

    /** A page of recent posts across all authors, newest first (interim feed). */
    Uni<List<PostDto>> getRecent(int page, int size);

    /**
     * A keyset-paged page of the post stream, newest first. Unlike {@link #getRecent(int, int)} the
     * cost does not grow with how deep the reader has scrolled, and a post published mid-scroll
     * cannot shift the page boundary past an unseen post. See {@code ADR-042}.
     */
    Uni<List<PostDto>> findPage(PostPageRequest request);
}
