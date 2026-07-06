package com.yoursay.observability;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class ApiException extends WebApplicationException {

    private final String domain;
    private final String errorCode;
    private final String publicMessage;

    public ApiException(String domain, String errorCode, Response.Status status, String detailMessage) {
        this(domain, errorCode, status, detailMessage, genericMessage(status));
    }

    public ApiException(String domain, String errorCode, Response.Status status, String detailMessage,
                        String publicMessage) {
        super(detailMessage, status);
        this.domain = domain;
        this.errorCode = errorCode;
        this.publicMessage = publicMessage;
    }

    public String domain() {
        return domain;
    }

    public String errorCode() {
        return errorCode;
    }

    public String publicMessage() {
        return publicMessage;
    }

    public int statusCode() {
        return getResponse().getStatus();
    }

    public static String genericMessage(Response.Status status) {
        if (status.getStatusCode() == Response.Status.BAD_REQUEST.getStatusCode()) {
            return "Invalid request.";
        }
        if (status.getStatusCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
            return "Authentication is required.";
        }
        if (status.getStatusCode() == Response.Status.FORBIDDEN.getStatusCode()) {
            return "You are not allowed to perform this action.";
        }
        if (status.getStatusCode() == Response.Status.NOT_FOUND.getStatusCode()) {
            return "The requested resource was not found.";
        }
        if (status.getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
            return "The request conflicts with the current state.";
        }
        return "The request could not be processed.";
    }
}
