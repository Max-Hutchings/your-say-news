package com.yoursay.user.user.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class UserApiException extends ApiException {

    private UserApiException(String errorCode, Response.Status status, String detailMessage) {
        super("user", errorCode, status, detailMessage);
    }

    private UserApiException(String errorCode, Response.Status status, String detailMessage, String publicMessage) {
        super("user", errorCode, status, detailMessage, publicMessage);
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

    public static UserApiException adminAccessRequired(String email) {
        return new UserApiException("USER_ADMIN_ACCESS_REQUIRED", Response.Status.FORBIDDEN,
                "Active database administrator access is required for subject email=" + email);
    }

    public static UserApiException inactive(String email) {
        return new UserApiException("USER_ACCOUNT_INACTIVE", Response.Status.FORBIDDEN,
                "The application account is inactive for subject email=" + email,
                "This account is inactive.");
    }

    public static UserApiException subjectLookupForbidden(String subjectEmail, String requestedEmail) {
        return new UserApiException("USER_SUBJECT_LOOKUP_FORBIDDEN", Response.Status.FORBIDDEN,
                "Subject email=" + subjectEmail + " attempted to resolve email=" + requestedEmail);
    }
}
