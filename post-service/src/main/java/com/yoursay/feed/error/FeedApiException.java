package com.yoursay.feed.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class FeedApiException extends ApiException {

    private FeedApiException(String errorCode, Response.Status status, String detailMessage) {
        super("feed", errorCode, status, detailMessage);
    }

    /**
     * A cursor the service did not mint. Rejected rather than treated as "start again", so a client
     * bug surfaces as an error instead of silently restarting the reader at the top of the feed.
     */
    public static FeedApiException invalidCursor(String cursor) {
        return new FeedApiException("FEED_CURSOR_INVALID", Response.Status.BAD_REQUEST,
                "Feed cursor is not a valid pagination token: cursor=" + cursor);
    }
}
