package com.yoursay.unwrapped.error;

import com.yoursay.platform.observability.ApiException;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

public class UnwrappedApiException extends ApiException {
    private UnwrappedApiException(String code, Response.Status status, String message) {
        super("unwrapped", code, status, message);
    }

    public static UnwrappedApiException storyMissing(UUID storyId) {
        return new UnwrappedApiException("UNWRAPPED_STORY_NOT_FOUND", Response.Status.NOT_FOUND,
                "Unwrapped story was not found: storyId=" + storyId);
    }

    public static UnwrappedApiException postMissing(Long postId) {
        return new UnwrappedApiException("UNWRAPPED_POST_NOT_FOUND", Response.Status.NOT_FOUND,
                "Post was not found: postId=" + postId);
    }

    public static UnwrappedApiException storyNotAvailable(UUID storyId) {
        return new UnwrappedApiException("UNWRAPPED_STORY_NOT_AVAILABLE", Response.Status.BAD_REQUEST,
                "Story is not approved for this post: storyId=" + storyId);
    }

    public static UnwrappedApiException optionNotAvailable(Long optionId) {
        return new UnwrappedApiException("UNWRAPPED_OPTION_NOT_AVAILABLE", Response.Status.BAD_REQUEST,
                "Follow-up option is not available on this post: optionId=" + optionId);
    }

    public static UnwrappedApiException userMissing() {
        return new UnwrappedApiException("UNWRAPPED_USER_NOT_FOUND", Response.Status.UNAUTHORIZED,
                "Authenticated user does not have a local account");
    }

    public static UnwrappedApiException invalidReviewState(UUID storyId) {
        return new UnwrappedApiException("UNWRAPPED_REVIEW_STATE_INVALID", Response.Status.CONFLICT,
                "Only draft stories can be reviewed: storyId=" + storyId);
    }
}
