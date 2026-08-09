package com.yoursay.feed.service;

import com.yoursay.feed.error.FeedApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The feed cursor is the whole pagination contract in one token: it names the last row of a page in
 * the immutable {@code (createdAt, id)} order so the next page resumes exactly there. These tests
 * pin the round trip (including sub-millisecond precision, which a naive epoch-millis encoding would
 * silently lose) and that a broken token fails loudly with 400 rather than restarting the reader's
 * feed from the top.
 */
class FeedCursorTest {

    @Test
    void roundTripsTheKeysetPositionIncludingSubMillisecondPrecision() {
        Instant createdAt = Instant.parse("2026-07-09T11:22:33.123456789Z");

        FeedCursor decoded = FeedCursor.decode(FeedCursor.encode(createdAt, 4242L));

        assertEquals(createdAt, decoded.createdAt());
        assertEquals(4242L, decoded.postId());
    }

    @Test
    void twoPostsInTheSameInstantGetDistinctCursors() {
        Instant sameInstant = Instant.parse("2026-07-09T11:22:33Z");

        String first = FeedCursor.encode(sameInstant, 10L);
        String second = FeedCursor.encode(sameInstant, 11L);

        assertTrue(!first.equals(second), "id must be part of the cursor, not just the timestamp");
        assertEquals(10L, FeedCursor.decode(first).postId());
        assertEquals(11L, FeedCursor.decode(second).postId());
    }

    @Test
    void absentCursorMeansTheFirstPage() {
        assertNull(FeedCursor.decode(null));
        assertNull(FeedCursor.decode(""));
        assertNull(FeedCursor.decode("   "));
    }

    @Test
    void tokenIsUrlSafeSoItSurvivesAQueryString() {
        String token = FeedCursor.encode(Instant.parse("2026-07-09T11:22:33.123456789Z"), 4242L);

        assertEquals(-1, token.indexOf('+'));
        assertEquals(-1, token.indexOf('/'));
        assertEquals(-1, token.indexOf('='));
    }

    @Test
    void rejectsTokensThatAreNotValidBase64() {
        FeedApiException error = assertThrows(FeedApiException.class,
                () -> FeedCursor.decode("!!! not base64 !!!"));

        assertEquals(400, error.statusCode());
        assertEquals("FEED_CURSOR_INVALID", error.errorCode());
    }

    @Test
    void rejectsWellFormedBase64ThatIsNotACursor() {
        assertThrows(FeedApiException.class, () -> FeedCursor.decode(base64("no-separator-here")));
        assertThrows(FeedApiException.class, () -> FeedCursor.decode(base64("not-an-instant|5")));
        assertThrows(FeedApiException.class,
                () -> FeedCursor.decode(base64("2026-07-09T11:22:33Z|not-a-number")));
        assertThrows(FeedApiException.class, () -> FeedCursor.decode(base64("2026-07-09T11:22:33Z|")));
        assertThrows(FeedApiException.class, () -> FeedCursor.decode(base64("|42")));
    }

    private static String base64(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
