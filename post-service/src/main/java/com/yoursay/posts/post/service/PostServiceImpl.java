package com.yoursay.posts.service;

import com.yoursay.posts.dto.PresignResponse;

import com.yoursay.posts.dto.PresignRequest;

import com.yoursay.posts.dto.CreatePostRequest;

import com.yoursay.posts.dto.VoteOptionDto;

import com.yoursay.posts.dto.PostDto;

import com.yoursay.posts.dto.PostMediaDto;

import com.yoursay.posts.dto.PostPageRequest;
import com.yoursay.posts.dto.PostCreationProvenance;
import com.yoursay.posts.dto.PostSourceDto;

import com.yoursay.posts.*;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.posts.error.PostApiException;
import com.yoursay.posts.model.Post;
import com.yoursay.posts.model.PostMedia;
import com.yoursay.posts.model.PostMediaUpload;
import com.yoursay.posts.model.PostMediaUploadRepository;
import com.yoursay.posts.model.PostRepository;
import com.yoursay.posts.model.PostVoteOption;
import com.yoursay.posts.model.PostVoteOptionRepository;
import com.yoursay.posts.model.PostSource;
import com.yoursay.posts.model.PostSourceRepository;
import com.yoursay.posts.model.VotingOptionRules;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.topics.TopicService;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PostServiceImpl implements PostService {

    static final int DEFAULT_PAGE_SIZE = 5;
    static final int MAX_PAGE_SIZE = 50;

    @Inject
    PostRepository postRepository;

    @Inject
    PostMediaUploadRepository uploadRepository;

    @Inject
    PostVoteOptionRepository optionRepository;

    @Inject
    PostSourceRepository sourceRepository;

    @Inject
    MediaStorageService mediaStorage;

    @Inject
    DomainMetrics metrics;

    @Inject
    UserServiceClient userServiceClient;

    @Inject
    TopicService topicService;

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
                                    author.userId(), request.mediaType(), upload.s3Key(), request.contentType(), expiresAt))
                            .replaceWith(new PresignResponse(
                                    upload.s3Key(), upload.uploadUrl(), upload.expiresInSeconds()));
                })
                .invoke(() -> recordMetric("presignUpload", true))
                .onFailure().invoke(() -> recordMetric("presignUpload", false));
    }

    @Override
    @WithTransaction
    public Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request) {
        return create(authorEmail, authorization, request, null);
    }

    @Override
    @WithTransaction
    public Uni<PostDto> create(String authorEmail, String authorization, CreatePostRequest request,
                               PostCreationProvenance provenance) {
        List<CreatePostRequest.Media> media = requestedMedia(request);
        List<VotingOptionRules.Definition> optionDefinitions = normalizeVotingOptions(request);
        validateMedia(media);

        return resolveAuthor(authorEmail, authorization)
                .flatMap(author -> existingAiPost(provenance)
                        .flatMap(existing -> existing != null
                                ? decoratePost(existing)
                                : consumeUploads(media, author.userId())
                                        .chain(() -> saveWithTopicTags(
                                                assemblePost(author.userId(), request, media,
                                                        optionDefinitions, provenance),
                                                request.topicTagIds()))))
                .invoke(() -> recordMetric("create", true))
                .onFailure().invoke(() -> recordMetric("create", false));
    }

    private Uni<Post> existingAiPost(PostCreationProvenance provenance) {
        return provenance == null
                ? Uni.createFrom().nullItem()
                : postRepository.getByAiDraftId(provenance.pepperDraftId());
    }

    private Uni<PostDto> decoratePost(Post post) {
        return optionRepository.listByPostId(post.getId())
                .flatMap(options -> sourceRepository.listByPostIds(List.of(post.getId()))
                        .map(sources -> toDto(post, options, sources)))
                .flatMap(dto -> topicService.effectiveTagsForPosts(List.of(post.getId()))
                        .map(tags -> dto.withTopicTags(tags.getOrDefault(post.getId(), List.of()))));
    }

    private static List<CreatePostRequest.Media> requestedMedia(CreatePostRequest request) {
        return request.media() == null ? List.of() : request.media();
    }

    /** Fills in the option set the voting type implies when the author supplied none. */
    private static List<VotingOptionRules.Definition> normalizeVotingOptions(CreatePostRequest request) {
        List<String> requestedLabels = request.voteOptions() == null
                ? List.of()
                : request.voteOptions().stream().map(CreatePostRequest.VoteOption::label).toList();
        return VotingOptionRules.normalize(request.votingType(), requestedLabels);
    }

    private static void validateMedia(List<CreatePostRequest.Media> media) {
        validateMediaKeysAreUnique(media);
        validateMediaCounts(media);
        media.forEach(item -> validateContentType(item.mediaType(), item.contentType()));
    }

    /**
     * Claims every presigned upload the request references, the video poster included, so an
     * attachment can never be reused or stolen from another author.
     */
    private Uni<Void> consumeUploads(List<CreatePostRequest.Media> media, Long authorId) {
        Uni<Void> claims = Uni.createFrom().voidItem();
        for (CreatePostRequest.Media item : media) {
            claims = claims.chain(() -> consumeUpload(
                    item.s3Key(), authorId, item.mediaType(), item.contentType()));
            if (item.posterS3Key() != null && !item.posterS3Key().isBlank()) {
                claims = claims.chain(() -> consumeUpload(
                        item.posterS3Key(), authorId, MediaType.IMAGE, null));
            }
        }
        return claims;
    }

    /** Author comes from the token; AI provenance is supplied only after server ownership checks. */
    private static Post assemblePost(Long authorId, CreatePostRequest request,
                                     List<CreatePostRequest.Media> media,
                                     List<VotingOptionRules.Definition> optionDefinitions,
                                     PostCreationProvenance provenance) {
        Post post = new Post(authorId, request.summary().trim(),
                request.supportQuestion().trim(), provenance != null);
        if (provenance != null) {
            post.setAiDraftId(provenance.pepperDraftId());
            for (int index = 0; index < provenance.sources().size(); index++) {
                PostSourceDto source = provenance.sources().get(index);
                post.addSource(new PostSource(post, source.url(), source.title(), source.publisher(), index));
            }
        }
        post.setCaseFor(emptyToNull(request.caseFor()));
        post.setCaseAgainst(emptyToNull(request.caseAgainst()));
        post.setJurisdiction(normalizeJurisdiction(request.jurisdiction()));
        post.configureVoting(
                request.votingType() == null ? VotingType.BINARY : request.votingType(),
                optionDefinitions);
        for (CreatePostRequest.Media item : media) {
            post.addMedia(new PostMedia(post, item.mediaType(), item.orientation(), item.s3Key(),
                    item.contentType(), emptyToNull(item.posterS3Key()), 0));
        }
        return post;
    }

    /**
     * Topic tags are validated and attached after the post exists, because the assignment rows need
     * its generated id. A bad topic id fails the whole transaction, so a post is never published
     * with a selection silently dropped.
     */
    private Uni<PostDto> saveWithTopicTags(Post post, List<String> topicTagIds) {
        return postRepository.savePost(post).flatMap(saved -> topicService
                .assignCreatorTags(saved.getId(), topicTagIds)
                .flatMap(tags -> sourceRepository.listByPostIds(List.of(saved.getId()))
                        .map(sources -> toDto(saved, saved.getVoteOptions(), sources)
                                .withTopicTags(tags))));
    }

    @Override
    @WithSession
    public Uni<PostDto> getById(Long id) {
        return postRepository.getPostById(id).flatMap(post -> post == null
                ? Uni.createFrom().nullItem()
                : optionRepository.listByPostId(id)
                        .flatMap(options -> sourceRepository.listByPostIds(List.of(id))
                                .map(sources -> toDto(post, options, sources)))
                        .flatMap(dto -> topicService.effectiveTagsForPosts(List.of(id))
                                .map(byPostId -> dto.withTopicTags(
                                        byPostId.getOrDefault(id, List.of())))));
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> getByUser(Long userId) {
        return postRepository.getPostsByUser(userId).flatMap(this::mapPostsWithOptions);
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> getRecent(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return postRepository.getRecent(safePage, safeSize).flatMap(this::mapPostsWithOptions);
    }

    @Override
    @WithSession
    public Uni<List<PostDto>> findPage(PostPageRequest request) {
        // The feed asks for one row beyond its page to detect the end of the feed, so the ceiling
        // here is one above the page cap rather than equal to it.
        int safeLimit = request.limit() <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(request.limit(), MAX_PAGE_SIZE + 1);
        PostMediaFilter mediaFilter = request.mediaFilter() == null
                ? PostMediaFilter.ANY
                : request.mediaFilter();
        return postRepository
                .findPageAfter(request.cursorCreatedAt(), request.cursorId(), mediaFilter,
                        request.topicTagId(), safeLimit)
                .flatMap(this::mapPostsWithOptions);
    }

    /**
     * Map a page of posts to DTOs, fetching every post's vote options and topics in a single query
     * each and grouping them by post id. Doing either per post made a page cost N+1 round trips.
     */
    private Uni<List<PostDto>> mapPostsWithOptions(List<Post> posts) {
        if (posts.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        List<Long> ids = posts.stream().map(Post::getId).toList();
        return optionRepository.listByPostIds(ids).flatMap(options ->
                sourceRepository.listByPostIds(ids).flatMap(sources -> {
            Map<Long, List<PostVoteOption>> optionsByPostId = options.stream()
                    .collect(Collectors.groupingBy(option -> option.getPost().getId()));
            Map<Long, List<PostSource>> sourcesByPostId = sources.stream()
                    .collect(Collectors.groupingBy(source -> source.getPost().getId()));
            return topicService.effectiveTagsForPosts(ids).map(tagsByPostId -> posts.stream()
                    .map(post -> toDto(
                                    post,
                                    optionsByPostId.getOrDefault(post.getId(), List.of()),
                                    sourcesByPostId.getOrDefault(post.getId(), List.of()))
                            .withTopicTags(tagsByPostId.getOrDefault(post.getId(), List.of())))
                    .toList());
        }));
    }

    private PostDto toDto(
            Post post, List<PostVoteOption> options, List<PostSource> sources) {
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
                post.getSummary(),
                post.getSupportQuestion(),
                post.getCaseFor(),
                post.getCaseAgainst(),
                post.getJurisdiction(),
                post.getVotingType(),
                options.stream()
                        .sorted((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                        .map(option -> new VoteOptionDto(option.getId(), option.getLabel(),
                                option.getOrdinal(), option.getSemanticKey()))
                        .toList(),
                post.isAiGenerated(),
                post.getCreatedAt(),
                media,
                // Decorated by the caller: a single post via getById, a page via mapPostsWithOptions.
                List.of(),
                sources.stream()
                        .sorted((left, right) -> Integer.compare(left.getOrdinal(), right.getOrdinal()))
                        .map(source -> new PostSourceDto(
                                source.getUrl(), source.getTitle(), source.getPublisher()))
                        .toList()
        );
    }

    private PostMediaDto toMediaDto(PostMedia m) {
        return new PostMediaDto(
                m.getMediaType(),
                m.getOrientation(),
                m.getS3Key(),
                m.getContentType(),
                m.getPosterS3Key(),
                mediaStorage.presignDownload(m.getS3Key()),
                mediaStorage.presignDownload(m.getPosterS3Key())
        );
    }

    private Uni<UserServiceClient.UserAccess> resolveAuthor(String authorEmail, String authorization) {
        return userServiceClient.getCurrentUserAccess(authorization)
                .onItem().ifNull().failWith(() ->
                        PostApiException.unknownAuthor(authorEmail))
                .invoke(access -> {
                    if (!access.isActiveOfficialPublisher()) {
                        throw PostApiException.publishingForbidden(access.userId());
                    }
                });
    }

    private Uni<Void> consumeUpload(String s3Key, Long userId, MediaType mediaType, String contentType) {
        return uploadRepository.findByKeyAndUser(s3Key, userId)
                .onItem().ifNull().failWith(() ->
                        PostApiException.uploadNotOwned(s3Key, userId))
                .invoke(upload -> {
                    Instant now = Instant.now();
                    if (upload.getAttachedAt() != null) {
                        throw PostApiException.uploadAlreadyAttached(s3Key);
                    }
                    if (upload.getExpiresAt().isBefore(now)) {
                        throw PostApiException.uploadExpired(s3Key);
                    }
                    if (upload.getMediaType() != mediaType) {
                        throw PostApiException.uploadTypeMismatch(s3Key, mediaType, upload.getMediaType());
                    }
                    if (contentType != null && !upload.getContentType().equalsIgnoreCase(contentType)) {
                        throw PostApiException.uploadContentTypeMismatch(s3Key, contentType, upload.getContentType());
                    }
                    upload.markAttached(now);
                })
                .replaceWithVoid();
    }

    private static void validateMediaKeysAreUnique(List<CreatePostRequest.Media> media) {
        Set<String> keys = new HashSet<>();
        for (CreatePostRequest.Media item : media) {
            if (item == null) {
                throw PostApiException.mediaItemRequired();
            }
            if (!keys.add(item.s3Key())) {
                throw PostApiException.mediaKeysNotUnique();
            }
            if (item.posterS3Key() != null && !item.posterS3Key().isBlank()
                    && !keys.add(item.posterS3Key())) {
                throw PostApiException.mediaKeysNotUnique();
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
            throw PostApiException.tooManyImages(images);
        }
        if (videos > 1) {
            throw PostApiException.tooManyVideos(videos);
        }
    }

    private static void validateContentType(MediaType mediaType, String contentType) {
        if (mediaType == null || contentType == null || contentType.isBlank()) {
            throw PostApiException.mediaTypeRequired(mediaType, contentType);
        }
        String normalized = contentType.toLowerCase();
        if (mediaType == MediaType.IMAGE && !normalized.startsWith("image/")) {
            throw PostApiException.invalidImageContentType(contentType);
        }
        if (mediaType == MediaType.VIDEO && !normalized.startsWith("video/")) {
            throw PostApiException.invalidVideoContentType(contentType);
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalizeJurisdiction(String value) {
        if (value == null || value.isBlank()) {
            return "GLOBAL";
        }
        String normalized = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{2,32}")) {
            throw PostApiException.invalidJurisdiction(value);
        }
        return normalized;
    }

    private void recordMetric(String operation, boolean success) {
        if (metrics != null) {
            metrics.recordOperation("posts", operation, success);
        }
    }
}
