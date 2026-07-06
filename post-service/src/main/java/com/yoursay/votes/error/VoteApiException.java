package com.yoursay.votes.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class VoteApiException extends ApiException {

    private VoteApiException(String errorCode, Response.Status status, String detailMessage) {
        super("votes", errorCode, status, detailMessage);
    }

    public static VoteApiException duplicateVote(Long postId, Long userId) {
        return new VoteApiException("VOTE_DUPLICATE", Response.Status.CONFLICT,
                "Duplicate vote rejected: postId=" + postId + ", userId=" + userId);
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
}
