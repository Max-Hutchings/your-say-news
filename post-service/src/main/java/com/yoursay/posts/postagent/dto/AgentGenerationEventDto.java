package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.postagent.PepperDraftStatus;

import java.util.UUID;

public record AgentGenerationEventDto(
        PepperDraftStatus status,
        UUID draftId,
        String replicaId,
        PepperPostDraftDto result,
        String errorMessage
) {
    public boolean terminal() {
        return status == PepperDraftStatus.FINISHED || status == PepperDraftStatus.FAILED;
    }
}
