package com.yoursay.topics;

import com.yoursay.topics.dto.CreateTopicRequest;
import com.yoursay.topics.dto.TopicTagDto;
import io.smallrye.mutiny.Uni;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Public contract for the governed topic tag catalogue and post relationships.
 *
 * <p>Reactive rather than imperative because its data is read inside the reactive {@code posts} and
 * {@code feed} pipelines — decorating a page of posts and validating an author's selection both
 * happen inside an existing Hibernate Reactive session.
 */
public interface TopicService {

    /** Maximum topics a single post can carry. */
    int MAX_TOPIC_TAGS_PER_POST = 3;

    /** The catalogue a reader sees: active topics only, in tab-strip order. */
    Uni<List<TopicTagDto>> listActive();

    /** The whole catalogue including retired topics — admin only. */
    Uni<List<TopicTagDto>> listAll();

    /**
     * Add a topic. The canonical id is derived from the label; the topic goes to the end of the
     * catalogue. Fails with 409 when the derived id is already taken.
     */
    Uni<TopicTagDto> create(CreateTopicRequest request);

    /** Retire or restore a topic. Retirement never removes existing assignments. */
    Uni<TopicTagDto> setActive(String topicTagId, boolean active);

    /**
     * Validate a selection and attach it to a post. Rejects more than
     * {@link #MAX_TOPIC_TAGS_PER_POST}, duplicates, and ids that are unknown or retired.
     */
    Uni<List<TopicTagDto>> assignCreatorTags(Long postId, List<String> topicTagIds);

    /**
     * The topics carried by each of {@code postIds}, keyed by post id, in one query. Posts with no
     * topics are absent from the map rather than mapped to an empty list.
     */
    Uni<Map<Long, List<TopicTagDto>>> effectiveTagsForPosts(Collection<Long> postIds);

    /**
     * Confirm a topic exists before the feed filters on it, so an unknown id is a 400 rather than an
     * empty page that looks like a dead category. Retired topics are accepted: their feed still
     * works for posts that already carry them.
     */
    Uni<Void> requireExists(String topicTagId);
}
