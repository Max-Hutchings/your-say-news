package com.yoursay.topics.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link PostTopic}: the assignment is unique per (post, topic) pair. */
public class PostTopicId implements Serializable {

    private Long postId;
    private String topicId;

    public PostTopicId() {
    }

    public PostTopicId(Long postId, String topicId) {
        this.postId = postId;
        this.topicId = topicId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostTopicId that)) {
            return false;
        }
        return Objects.equals(postId, that.postId) && Objects.equals(topicId, that.topicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, topicId);
    }
}
