package com.yoursay.topics.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Derives a canonical topic id from an admin's label — "Cost of living" becomes
 * {@code cost-of-living}, "War &amp; conflict" becomes {@code war-conflict}.
 *
 * <p>The server derives the id rather than accepting one so the catalogue cannot acquire ids that
 * disagree with their labels. The admin form previews the result using the same rule. The output
 * must satisfy the {@code ck_topic_id} check constraint ({@code ^[a-z0-9-]{2,64}$}); a label that
 * cannot produce one is rejected rather than silently mangled.
 */
public final class TopicSlug {

    private static final int MAX_LENGTH = 64;
    private static final int MIN_LENGTH = 2;

    private TopicSlug() {
    }

    /** The canonical id for {@code label}, or null when the label cannot produce a valid one. */
    public static String from(String label) {
        if (label == null) {
            return null;
        }
        // Decompose accents (é -> e + combining acute) so the marks can be stripped rather than
        // dropping the whole letter, keeping "Café culture" as "cafe-culture".
        String ascii = Normalizer.normalize(label, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.length() > MAX_LENGTH) {
            // Trim on a boundary so truncation never leaves a trailing hyphen.
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.length() < MIN_LENGTH ? null : slug;
    }
}
