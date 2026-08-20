package com.yoursay.posts.postagent.error;

import com.yoursay.observability.ApiException;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

public class AgentApiException extends ApiException {

    private AgentApiException(String errorCode, Response.Status status, String detailMessage) {
        super("agent", errorCode, status, detailMessage);
    }

    public static AgentApiException userMissing() {
        return new AgentApiException("AGENT_USER_NOT_FOUND", Response.Status.UNAUTHORIZED,
                "Authenticated publisher could not be resolved.");
    }

    public static AgentApiException userLookupFailed(int status) {
        return new AgentApiException("AGENT_USER_LOOKUP_FAILED", Response.Status.BAD_GATEWAY,
                "Publisher lookup failed with status=" + status);
    }

    public static AgentApiException publishingForbidden(Long userId) {
        return new AgentApiException("AGENT_PUBLISHING_FORBIDDEN", Response.Status.FORBIDDEN,
                "Pepper generation requires an active official publisher.");
    }

    public static AgentApiException draftMissing() {
        return new AgentApiException("AGENT_DRAFT_NOT_FOUND", Response.Status.NOT_FOUND,
                "Pepper draft was not found.");
    }

    public static AgentApiException replicaUnavailable() {
        return new AgentApiException("AGENT_REPLICA_UNAVAILABLE", Response.Status.SERVICE_UNAVAILABLE,
                "The Pepper generation replica is unavailable.");
    }

    public static AgentApiException versionConflict() {
        return new AgentApiException("AGENT_DRAFT_VERSION_CONFLICT", Response.Status.CONFLICT,
                "Pepper draft was changed elsewhere.");
    }

    public static AgentApiException citationInvalid() {
        return new AgentApiException("AGENT_CITATION_INVALID", Response.Status.BAD_REQUEST,
                "A citation was not present in Pepper's generated sources.");
    }

    public static AgentApiException draftNotReady() {
        return new AgentApiException("AGENT_DRAFT_NOT_READY", Response.Status.CONFLICT,
                "Pepper draft is not ready to publish or edit.");
    }

    public static AgentApiException draftInvalid() {
        return new AgentApiException("AGENT_DRAFT_INVALID", Response.Status.BAD_REQUEST,
                "Pepper draft contains invalid post fields.");
    }
}
