package com.yoursay.topics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An admin adding a topic to the catalogue (ADR-043).
 *
 * <p>The canonical id is <em>derived server-side</em> from the label rather than sent, so the client
 * and the database cannot disagree about it. The admin form shows the same derivation as a
 * read-only preview. {@code displayOrder} is not accepted either: a new topic always goes to the end
 * of the catalogue.
 */
public record CreateTopicRequest(
        @NotBlank
        @Size(max = 80)
        String label,
        @NotBlank
        @Size(max = 64)
        String displayGroup
) {
}
