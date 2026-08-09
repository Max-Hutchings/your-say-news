package com.yoursay.topics.dto;

/**
 * A topic as every surface sees it: the mobile tab strip and picker, the topic chips on a post, and
 * the admin ledger.
 *
 * @param id           canonical id — what {@code GET /feed?topic=} and post creation send
 * @param label        display copy
 * @param displayGroup groups the picker for scanning; not selectable and not a semantic parent
 * @param displayOrder tab-strip position, ascending
 * @param active       false for a retired topic: still shown on posts that carry it, never offered
 *                     as a new selection
 */
public record TopicDto(
        String id,
        String label,
        String displayGroup,
        int displayOrder,
        boolean active
) {
}
