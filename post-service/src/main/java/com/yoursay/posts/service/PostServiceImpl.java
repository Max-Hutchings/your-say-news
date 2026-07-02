package com.yoursay.posts.service;

import com.yoursay.posts.*;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.posts.model.Post;
import com.yoursay.posts.model.PostMedia;
import com.yoursay.posts.model.PostMediaUpload;
import com.yoursay.posts.model.PostMediaUploadRepository;
import com.yoursay.posts.model.PostRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PostServiceImpl implements PostService {

    static final int RECENT_LIMIT = 50;

    @Inject
    PostRepository postRepository;

    @Inject
    PostMediaUploadRepository uploadRepository;

    @Inject
    MediaStorageService mediaStorage;

    @RestClient
    UserServiceClient userServiceClient;

    @Override
    @WithTransaction
    public Uni<PresignResponse> presignUpload(String authorEmail, String authorization,
                                              PresignRequest request) {
        validateContentType(request.mediaType(), request.contentType());
        return resolveAuthor(authorEmail, authorization)
                .flatMap(author -> {
                    MediaStorageService.Upload upload = mediaStorage.presignUpload(
                            request.mediaType(), request.contentType());
                    Instant expiresAt = Instant.now().plusSeconds(upload.expiresInSeconds());
                    return uploadRepository.saveUpload(new PostMediaUpload(
                                    author.id(), request.mediaType(), upload.s3Key(), request.contentType(), expiresAt))
                            .replaceWith(new PresignResponse(
                                    upload.s3Key(), upload.uploadUrl(), upload.expiresInSeconds()));
                });
    }

    @Override
    @WithTransaction
    public Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request) {
        Log.infof("Creating post for author %s", authorEmail);
        List<CreatePostRequest.Media> media = request.media() == null ? List.of() : request.media();
        validateMediaKeysAreUnique(media);
        validateMediaCounts(media);
        media.forEach(m -> validateContentType(m.mediaType(), m.contentType()));

        return resolveAuthor(authorEmail, authorization)
                .flatMap(author -> {
                    Uni<Void> validation = Uni.createFrom().voidItem();
                    for (CreatePostRequest.Media item : media) {
                        validation = validation.chain(() -> consumeUpload(
                                item.s3Key(), author.id(), item.mediaType(), item.contentType()));
                        if (item.posterS3Key() != null && !item.posterS3Key().isBlank()) {
                            validation = validation.chain(() -> consumeUpload(
                                    item.posterS3Key(), author.id(), MediaType.IMAGE, null));
                        }
                    }
                    return validation.chain(() -> {
                        // Author from the token; body userId (if any) and isUnbiased are ignored/forced.
                        Post post = new Post(author.id(), request.title().trim(), request.summary().trim(),
                                request.supportQuestion().trim(), false);
                        for (CreatePostRequest.Media m : media) {
                            post.addMedia(new PostMedia(post, m.mediaType(), m.s3Key(),
                                    m.contentType(), emptyToNull(m.posterS3Key()), 0));
                        }
                        return postRepository.savePost(post).map(this::toDto);
                    });
                });
    }

    @Override
    @WithSession
    public Uni<PostDto> getById(Long id) {
        return postRepository.getPostById(id).map(this::toDto);
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> getByUser(Long userId) {
        return postRepository.getPostsByUser(userId)
                .map(posts -> posts.stream().map(this::toDto).toList());
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> getRecent() {
        return postRepository.getRecent(RECENT_LIMIT)
                .map(posts -> posts.stream().map(this::toDto).toList());
    }

    private PostDto toDto(Post post) {
        if (post == null) {
            return null;
        }
        List<PostMediaDto> media = post.getMedia().stream()
                .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                .map(this::toMediaDto)
                .toList();
        return new PostDto(
                post.getId(),
                post.getUserId(),
                post.getTitle(),
                post.getSummary(),
                post.getSupportQuestion(),
                post.isUnbiased(),
                post.getCreatedAt(),
                media
        );
    }

    private PostMediaDto toMediaDto(PostMedia m) {
        return new PostMediaDto(
                m.getMediaType(),
                m.getS3Key(),
                m.getContentType(),
                m.getPosterS3Key(),
                mediaStorage.presignDownload(m.getS3Key()),
                mediaStorage.presignDownload(m.getPosterS3Key())
        );
    }

    private Uni<UserServiceClient.UserRef> resolveAuthor(String authorEmail, String authorization) {
        return userServiceClient.getUserByEmail(authorEmail, authorization)
                .onItem().ifNull().failWith(() ->
                        new WebApplicationException("Unknown author: " + authorEmail, 401));
    }

    private Uni<Void> consumeUpload(String s3Key, Long userId, MediaType mediaType, String contentType) {
        return uploadRepository.findByKeyAndUser(s3Key, userId)
                .onItem().ifNull().failWith(() ->
                        new WebApplicationException("Media upload was not created for this user", 400))
                .invoke(upload -> {
                    Instant now = Instant.now();
                    if (upload.getAttachedAt() != null) {
                        throw new WebApplicationException("Media upload has already been attached", 400);
                    }
                    if (upload.getExpiresAt().isBefore(now)) {
                        throw new WebApplicationException("Media upload has expired", 400);
                    }
                    if (upload.getMediaType() != mediaType) {
                        throw new WebApplicationException("Media upload type does not match", 400);
                    }
                    if (contentType != null && !upload.getContentType().equalsIgnoreCase(contentType)) {
                        throw new WebApplicationException("Media upload content type does not match", 400);
                    }
                    upload.markAttached(now);
                })
                .replaceWithVoid();
    }

    private static void validateMediaKeysAreUnique(List<CreatePostRequest.Media> media) {
        Set<String> keys = new HashSet<>();
        for (CreatePostRequest.Media item : media) {
            if (item == null) {
                throw new WebApplicationException("Media item is required", 400);
            }
            if (!keys.add(item.s3Key())) {
                throw new WebApplicationException("Media upload keys must be unique", 400);
            }
            if (item.posterS3Key() != null && !item.posterS3Key().isBlank()
                    && !keys.add(item.posterS3Key())) {
                throw new WebApplicationException("Media upload keys must be unique", 400);
            }
        }
    }

    /**
     * A post carries either up to five images (shown as a swipeable carousel) or a single video
     * (auto-played in the feed). Guard both ceilings; a mix is allowed but bounded by each count.
     */
    private static void validateMediaCounts(List<CreatePostRequest.Media> media) {
        long images = media.stream().filter(m -> m.mediaType() == MediaType.IMAGE).count();
        long videos = media.stream().filter(m -> m.mediaType() == MediaType.VIDEO).count();
        if (images > 5) {
            throw new WebApplicationException("A post can have at most 5 images", 400);
        }
        if (videos > 1) {
            throw new WebApplicationException("A post can have at most 1 video", 400);
        }
    }

    private static void validateContentType(MediaType mediaType, String contentType) {
        if (mediaType == null || contentType == null || contentType.isBlank()) {
            throw new WebApplicationException("Media type and content type are required", 400);
        }
        String normalized = contentType.toLowerCase();
        if (mediaType == MediaType.IMAGE && !normalized.startsWith("image/")) {
            throw new WebApplicationException("Image uploads must use an image content type", 400);
        }
        if (mediaType == MediaType.VIDEO && !normalized.startsWith("video/")) {
            throw new WebApplicationException("Video uploads must use a video content type", 400);
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
