package com.yoursay.topics.model;

import java.io.Serializable;
import java.util.Objects;

public class EffectivePostTopicTagId implements Serializable {
    private Long postId;
    private String topicTagId;

    public EffectivePostTopicTagId() {
    }

    public EffectivePostTopicTagId(Long postId, String topicTagId) {
        this.postId = postId;
        this.topicTagId = topicTagId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EffectivePostTopicTagId that)) {
            return false;
        }
        return Objects.equals(postId, that.postId) && Objects.equals(topicTagId, that.topicTagId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, topicTagId);
    }
}
