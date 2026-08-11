package com.yoursay.topics.service;

import com.yoursay.observability.DomainMetrics;
import com.yoursay.topics.TopicService;
import com.yoursay.topics.dto.CreateTopicRequest;
import com.yoursay.topics.dto.TopicTagDto;
import com.yoursay.topics.error.TopicApiException;
import com.yoursay.topics.model.EffectivePostTopicTag;
import com.yoursay.topics.model.PostTopicTagRepository;
import com.yoursay.topics.model.Topic;
import com.yoursay.topics.model.TopicRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TopicServiceImpl implements TopicService {

    @Inject
    TopicRepository topicRepository;

    @Inject
    PostTopicTagRepository postTopicTagRepository;

    @Inject
    DomainMetrics metrics;

    @Override
    @WithSession
    public Uni<List<TopicTagDto>> listActive() {
        return topicRepository.listActive().map(TopicServiceImpl::toDtos)
                .invoke(() -> recordMetric("listActive", true))
                .onFailure().invoke(() -> recordMetric("listActive", false));
    }

    @Override
    @WithSession
    public Uni<List<TopicTagDto>> listAll() {
        return topicRepository.listAll().map(TopicServiceImpl::toDtos)
                .invoke(() -> recordMetric("listAll", true))
                .onFailure().invoke(() -> recordMetric("listAll", false));
    }

    @Override
    @WithTransaction
    public Uni<TopicTagDto> create(CreateTopicRequest request) {
        String label = request.label().trim();
        String id = TopicSlug.from(label);
        if (id == null) {
            return Uni.createFrom().failure(TopicApiException.unusableLabel(request.label()));
        }

        // Checked before insert so a duplicate is a clean 409 rather than a constraint violation
        // surfacing as a 500.
        return topicRepository.findByIdentifier(id)
                .flatMap(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().failure(TopicApiException.topicAlreadyExists(id));
                    }
                    return topicRepository.nextDisplayOrder().flatMap(order -> topicRepository
                            .save(new Topic(id, label, request.displayGroup().trim(), order)));
                })
                .map(TopicServiceImpl::toDto)
                .invoke(() -> recordMetric("create", true))
                .onFailure().invoke(() -> recordMetric("create", false));
    }

    @Override
    @WithTransaction
    public Uni<TopicTagDto> setActive(String topicTagId, boolean active) {
        return topicRepository.findByIdentifier(topicTagId)
                .onItem().ifNull().failWith(() -> TopicApiException.topicNotFound(topicTagId))
                .invoke(topic -> topic.setActive(active))
                .map(TopicServiceImpl::toDto)
                .invoke(() -> recordMetric("setActive", true))
                .onFailure().invoke(() -> recordMetric("setActive", false));
    }

    @Override
    @WithTransaction
    public Uni<List<TopicTagDto>> assignCreatorTags(Long postId, List<String> topicTagIds) {
        List<String> requested = topicTagIds == null ? List.of() : topicTagIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .toList();
        if (requested.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        if (requested.size() > MAX_TOPIC_TAGS_PER_POST) {
            return Uni.createFrom().failure(
                    TopicApiException.tooManyTopics(requested.size(), MAX_TOPIC_TAGS_PER_POST));
        }
        Set<String> unique = new LinkedHashSet<>(requested);
        if (unique.size() != requested.size()) {
            return Uni.createFrom().failure(TopicApiException.duplicateTopics(requested));
        }

        return topicRepository.listActiveByIds(unique).flatMap(found -> {
            if (found.size() != unique.size()) {
                Set<String> known = found.stream().map(Topic::getId).collect(Collectors.toSet());
                List<String> missing = unique.stream().filter(id -> !known.contains(id)).toList();
                return Uni.createFrom().failure(TopicApiException.unknownTopics(missing));
            }
            return postTopicTagRepository.assignCreatorTags(postId, List.copyOf(unique))
                    .replaceWith(() -> toDtos(found));
        })
                .invoke(() -> recordMetric("assignCreatorTags", true))
                .onFailure().invoke(() -> recordMetric("assignCreatorTags", false));
    }

    @Override
    @WithSession
    public Uni<Map<Long, List<TopicTagDto>>> effectiveTagsForPosts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }
        return postTopicTagRepository.listEffectiveByPostIds(postIds).map(effectiveTags -> {
            Map<Long, List<TopicTagDto>> byPostId = new java.util.HashMap<>();
            for (EffectivePostTopicTag effectiveTag : effectiveTags) {
                byPostId.computeIfAbsent(effectiveTag.getPostId(), key -> new ArrayList<>())
                        .add(toDto(effectiveTag.getTopicTag()));
            }
            return Map.copyOf(byPostId);
        });
    }

    @Override
    @WithSession
    public Uni<Void> requireExists(String topicTagId) {
        // Retired topics pass: their feed still serves the posts that already carry them.
        return topicRepository.findByIdentifier(topicTagId)
                .onItem().ifNull().failWith(() -> TopicApiException.unknownFeedTopic(topicTagId))
                .replaceWithVoid();
    }

    private static List<TopicTagDto> toDtos(List<Topic> topics) {
        return topics.stream().map(TopicServiceImpl::toDto).toList();
    }

    private static TopicTagDto toDto(Topic topic) {
        return new TopicTagDto(topic.getId(), topic.getLabel(), topic.getDisplayGroup(),
                topic.getDisplayOrder(), topic.isActive());
    }

    private void recordMetric(String operation, boolean success) {
        if (metrics != null) {
            metrics.recordOperation("topics", operation, success);
        }
    }
}
