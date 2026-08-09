package com.yoursay.topics.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The label -> canonical id rule. Every id it produces has to satisfy the {@code ck_topic_id} check
 * constraint ({@code ^[a-z0-9-]{2,64}$}), because a slug that violates it reaches the database as a
 * 500 instead of a validation error.
 */
class TopicSlugTest {

    @Test
    void producesTheCanonicalIdsAlreadyShippedInTheCatalogue() {
        // These exact ids are in migration 0015. If the rule drifts, an admin re-adding a topic by
        // its label would mint a near-duplicate rather than colliding with the existing row.
        assertEquals("cost-of-living", TopicSlug.from("Cost of living"));
        assertEquals("war-conflict", TopicSlug.from("War & conflict"));
        assertEquals("artificial-intelligence", TopicSlug.from("Artificial intelligence"));
        assertEquals("housing", TopicSlug.from("Housing"));
    }

    @Test
    void collapsesPunctuationAndTrimsTheEdgesRatherThanEmittingStrayHyphens() {
        // "-housing-" would pass a naive regex but reads as a different topic and sorts oddly.
        assertEquals("policy-the-n-h-s", TopicSlug.from("  Policy: the N.H.S.!  "));
        assertEquals("rail-air", TopicSlug.from("--Rail / Air--"));
    }

    @Test
    void stripsAccentsInsteadOfDroppingTheLetter() {
        // Dropping the accented letter would give "caf-culture"; the mark is what must go.
        assertEquals("cafe-culture", TopicSlug.from("Café culture"));
    }

    @Test
    void rejectsLabelsThatCannotProduceAValidId() {
        // Each of these would otherwise reach the database and fail ck_topic_id as a 500.
        assertNull(TopicSlug.from("!!!"), "punctuation only");
        assertNull(TopicSlug.from("X"), "single character is below the 2-char floor");
        assertNull(TopicSlug.from("   "), "blank");
        assertNull(TopicSlug.from(null));
    }

    @Test
    void truncatesAnOverlongLabelOnAHyphenBoundary() {
        String label = "a".repeat(70);
        String slug = TopicSlug.from(label);

        assertEquals(64, slug.length());
        assertEquals("a".repeat(64), slug);
    }

    @Test
    void neverLeavesATrailingHyphenAfterTruncation() {
        // The 65th character is where the cut lands; without the boundary trim the id would end
        // in "-" and violate neither length nor charset, but read as an unfinished word.
        String label = "b".repeat(63) + " tail";
        String slug = TopicSlug.from(label);

        assertEquals("b".repeat(63), slug);
    }
}
