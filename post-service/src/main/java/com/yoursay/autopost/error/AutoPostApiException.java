package com.yoursay.autopost.error;

import com.yoursay.platform.observability.ApiException;
import jakarta.ws.rs.core.Response;

public class AutoPostApiException extends ApiException {

    private AutoPostApiException(String code, Response.Status status, String detail, String message) {
        super("autopost", code, status, detail, message);
    }

    public static AutoPostApiException adminAccessRequired() {
        return new AutoPostApiException("ADMIN_ACCESS_REQUIRED", Response.Status.FORBIDDEN,
                "Active database administrator access is required for auto-post.",
                "Active administrator access is required.");
    }

    public static AutoPostApiException runMissing() {
        return new AutoPostApiException("AUTO_POST_RUN_NOT_FOUND", Response.Status.NOT_FOUND,
                "Auto-post run was not found.", "The auto-post run was not found.");
    }

    public static AutoPostApiException candidateMissing() {
        return new AutoPostApiException("AUTO_POST_CANDIDATE_NOT_FOUND", Response.Status.NOT_FOUND,
                "Auto-post candidate was not found in the run.", "The selected story was not found.");
    }

    public static AutoPostApiException selectionConflict() {
        return new AutoPostApiException("AUTO_POST_SELECTION_CONFLICT", Response.Status.CONFLICT,
                "The run is not ready for this candidate selection.",
                "This story list has already moved to the next step.");
    }

    public static AutoPostApiException draftRetryConflict() {
        return new AutoPostApiException("AUTO_POST_DRAFT_RETRY_CONFLICT", Response.Status.CONFLICT,
                "The run does not have a failed post-agent draft to retry.",
                "Only a failed post-agent draft can be retried.");
    }

    public static AutoPostApiException officialAccountUnavailable() {
        return new AutoPostApiException("AUTO_POST_OFFICIAL_ACCOUNT_UNAVAILABLE",
                Response.Status.SERVICE_UNAVAILABLE,
                "The fixed official account is missing or cannot publish.",
                "The Your Say News publishing account is unavailable.");
    }

    public static AutoPostApiException approvalConflict() {
        return new AutoPostApiException("AUTO_POST_APPROVAL_CONFLICT", Response.Status.CONFLICT,
                "The run does not have a completed draft ready for approval.",
                "This draft is not ready for approval.");
    }

    public static AutoPostApiException publicationFailed() {
        return new AutoPostApiException("AUTO_POST_PUBLICATION_FAILED", Response.Status.BAD_GATEWAY,
                "The approved auto-post could not be published.",
                "The approved post could not be published. Try again.");
    }
}
