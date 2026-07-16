package com.yoursay.agent.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class AgentApiException extends ApiException {

    private AgentApiException(String errorCode, Response.Status status, String detailMessage) {
        super("agent", errorCode, status, detailMessage);
    }

    public static AgentApiException userMissing(String email) {
        return new AgentApiException("AGENT_USER_NOT_FOUND", Response.Status.UNAUTHORIZED,
                "Cannot create or read agent job because user lookup failed: email=" + email);
    }

    public static AgentApiException userLookupFailed(String email, int status) {
        return new AgentApiException("AGENT_USER_LOOKUP_FAILED", Response.Status.BAD_GATEWAY,
                "User lookup failed: email=" + email + ", status=" + status);
    }
}
