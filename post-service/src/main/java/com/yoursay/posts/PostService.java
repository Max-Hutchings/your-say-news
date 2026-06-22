package com.yoursay.posts;

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
     * server-side by calling user-service; {@code authorization} is the caller's bearer header,
     * forwarded on that role-gated call. {@code isUnbiased} is forced false.
     */
    Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request);

    /** A single post with presigned media URLs, or null if it does not exist. */
    Uni<PostDto> getById(Long id);

    /** Posts by the given author, newest first. */
    Uni<List<PostDto>> getByUser(Long userId);

    /** Recent posts across all authors, newest first (interim feed). */
    Uni<List<PostDto>> getRecent();
}
