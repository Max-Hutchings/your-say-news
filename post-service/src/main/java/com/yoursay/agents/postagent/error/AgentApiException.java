package com.yoursay.agents.postagent.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

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

    public static AgentApiException publishingForbidden(Long userId) {
        return new AgentApiException("AGENT_PUBLISHING_FORBIDDEN", Response.Status.FORBIDDEN,
                "Agent generation requires an active official publisher: userId=" + userId);
    }

    public static AgentApiException jobMissing(UUID jobId) {
        return new AgentApiException("AGENT_JOB_NOT_FOUND", Response.Status.NOT_FOUND,
                "Agent job was not found: jobId=" + jobId);
    }
}
