package com.yoursay.user.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class UserApiException extends ApiException {

    private UserApiException(String errorCode, Response.Status status, String detailMessage) {
        super("user", errorCode, status, detailMessage);
    }

    public static UserApiException missingIdentity(String email, String firstName, String lastName) {
        return new UserApiException("USER_IDENTITY_CLAIMS_MISSING", Response.Status.BAD_REQUEST,
                "Missing user identity claims: email=" + email + ", firstName=" + firstName + ", lastName=" + lastName);
    }

    public static UserApiException notFoundForAuthenticatedSubject(String email) {
        return new UserApiException("USER_NOT_FOUND_FOR_SUBJECT", Response.Status.NOT_FOUND,
                "No user account exists for authenticated subject email=" + email);
    }

    public static UserApiException notFound(long userId) {
        return new UserApiException("USER_NOT_FOUND", Response.Status.NOT_FOUND,
                "No user account exists for id=" + userId);
    }
}
