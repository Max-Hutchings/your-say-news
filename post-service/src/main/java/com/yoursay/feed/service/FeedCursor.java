package com.yoursay.feed.service;

import com.yoursay.feed.error.FeedApiException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * The feed's pagination token: the {@code (createdAt, id)} position of the last post on a page, so
 * the next page resumes at exactly that row. Encoded as url-safe base64 and treated as opaque by
 * clients — the encoding is ours to change.
 *
 * <p>The timestamp is carried as an ISO-8601 string rather than epoch millis so nanosecond precision
 * survives the round trip; losing it would make two posts in the same millisecond ambiguous and let
 * one slip past a page boundary.
 */
public record FeedCursor(Instant createdAt, long postId) {

    private static final char SEPARATOR = '|';

    public static String encode(Instant createdAt, Long postId) {
        String raw = createdAt.toString() + SEPARATOR + postId;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decode a token, or null when there is none — an absent cursor means the first page. */
    public static FeedCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String raw;
        try {
            raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw FeedApiException.invalidCursor(token);
        }

        int separator = raw.lastIndexOf(SEPARATOR);
        if (separator <= 0 || separator == raw.length() - 1) {
            throw FeedApiException.invalidCursor(token);
        }
        try {
            return new FeedCursor(
                    Instant.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
        } catch (DateTimeParseException | NumberFormatException e) {
            throw FeedApiException.invalidCursor(token);
        }
    }
}
