package com.yoursay.topics.dto;

/** Public summary for one governed topic tag. */
public record TopicTagDto(
        String id,
        String label,
        String displayGroup,
        int displayOrder,
        boolean active
) {
}
