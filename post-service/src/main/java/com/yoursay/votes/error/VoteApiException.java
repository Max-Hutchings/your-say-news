package com.yoursay.votes.error;

import com.yoursay.platform.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class VoteApiException extends ApiException {

    private VoteApiException(String errorCode, Response.Status status, String detailMessage) {
        super("votes", errorCode, status, detailMessage);
    }

    private VoteApiException(String errorCode, Response.Status status, String detailMessage,
                             boolean expectedRejection) {
        super("votes", errorCode, status, detailMessage, ApiException.genericMessage(status), expectedRejection);
    }

    /** One vote per user per post is the product rule, so a second attempt is expected, not a fault. */
    public static VoteApiException duplicateVote(Long postId, Long userId) {
        return new VoteApiException("VOTE_DUPLICATE", Response.Status.CONFLICT,
                "Duplicate vote rejected: postId=" + postId + ", userId=" + userId, true);
    }

    public static VoteApiException postMissing(Long postId) {
        return new VoteApiException("VOTE_POST_MISSING", Response.Status.NOT_FOUND,
                "Cannot vote: no post exists with id=" + postId);
    }

    public static VoteApiException invalidVote(String reason) {
        return new VoteApiException("VOTE_INVALID", Response.Status.BAD_REQUEST,
                "Invalid vote request: " + reason);
    }

    public static VoteApiException optionNotAvailable(Long postId, Long optionId) {
        return new VoteApiException("VOTE_OPTION_NOT_AVAILABLE", Response.Status.BAD_REQUEST,
                "Selected option is not available on the post: postId=" + postId + ", optionId=" + optionId);
    }

    /** Results stay locked until the caller votes, so this refusal is the feature working. */
    public static VoteApiException resultsLocked(Long postId) {
        return new VoteApiException("VOTE_RESULTS_LOCKED", Response.Status.FORBIDDEN,
                "Sentiment results are locked until the caller has voted on post " + postId, true);
    }

    public static VoteApiException unknownAxis(String axis) {
        return new VoteApiException("VOTE_UNKNOWN_AXIS", Response.Status.BAD_REQUEST,
                "Not a real characteristic breakdown axis: " + axis);
    }

    public static VoteApiException userMissing(String callerEmail) {
        return new VoteApiException("VOTE_USER_MISSING", Response.Status.UNAUTHORIZED,
                "No user account for authenticated vote caller: email=" + callerEmail);
    }

    public static VoteApiException userLookupFailed(String callerEmail, int status) {
        Response.Status responseStatus = Response.Status.fromStatusCode(status);
        if (responseStatus == null) {
            responseStatus = Response.Status.BAD_GATEWAY;
        }
        return new VoteApiException("VOTE_USER_LOOKUP_FAILED", responseStatus,
                "Could not resolve vote caller user id: email=" + callerEmail + ", userServiceStatus=" + status);
    }

    public static VoteApiException unresolvedIncomeRange() {
        return new VoteApiException("VOTE_INCOME_RANGE_UNRESOLVED",
                Response.Status.INTERNAL_SERVER_ERROR,
                "Stored income cohort does not reference a published country-specific range");
    }
}
