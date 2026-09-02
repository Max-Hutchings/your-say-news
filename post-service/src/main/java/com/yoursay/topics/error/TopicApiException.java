package com.yoursay.topics.error;

import com.yoursay.platform.observability.ApiException;
import jakarta.ws.rs.core.Response;

import java.util.Collection;

public class TopicApiException extends ApiException {

    private TopicApiException(String errorCode, Response.Status status, String detailMessage) {
        super("topics", errorCode, status, detailMessage);
    }

    /**
     * A post named a topic that is not in the catalogue, or one that has been retired. Rejected
     * rather than dropped so an author never publishes believing a topic was applied.
     */
    public static TopicApiException unknownTopics(Collection<String> topicTagIds) {
        return new TopicApiException("TOPIC_UNKNOWN", Response.Status.BAD_REQUEST,
                "Topic tags are not in the catalogue or have been retired: topicTagIds="
                        + topicTagIds);
    }

    public static TopicApiException tooManyTopics(int count, int max) {
        return new TopicApiException("TOPIC_TOO_MANY", Response.Status.BAD_REQUEST,
                "A post carries at most " + max + " topic tags: count=" + count);
    }

    public static TopicApiException duplicateTopics(Collection<String> topicTagIds) {
        return new TopicApiException("TOPIC_DUPLICATE_SELECTION", Response.Status.BAD_REQUEST,
                "Topic tag selection contains the same tag twice: topicTagIds=" + topicTagIds);
    }

    /** The feed was asked for a topic that does not exist — a client bug, not an empty category. */
    public static TopicApiException unknownFeedTopic(String topicTagId) {
        return new TopicApiException("TOPIC_FEED_UNKNOWN", Response.Status.BAD_REQUEST,
                "Feed requested an unknown topic tag: topicTagId=" + topicTagId);
    }

    public static TopicApiException topicNotFound(String topicId) {
        return new TopicApiException("TOPIC_NOT_FOUND", Response.Status.NOT_FOUND,
                "Topic tag does not exist: topicTagId=" + topicId);
    }

    public static TopicApiException topicAlreadyExists(String topicId) {
        return new TopicApiException("TOPIC_ALREADY_EXISTS", Response.Status.CONFLICT,
                "A topic tag with this canonical ID already exists: topicTagId=" + topicId);
    }

    /**
     * The label did not reduce to a usable canonical id — e.g. it was punctuation only, or a single
     * character once slugified.
     */
    public static TopicApiException unusableLabel(String label) {
        return new TopicApiException("TOPIC_LABEL_UNUSABLE", Response.Status.BAD_REQUEST,
                "Topic tag label does not produce a valid canonical ID: label=" + label);
    }
}
