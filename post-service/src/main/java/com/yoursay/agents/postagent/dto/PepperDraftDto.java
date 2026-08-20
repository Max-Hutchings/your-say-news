package com.yoursay.agents.postagent.dto;

import com.yoursay.agents.postagent.PepperDraftStatus;

import java.util.UUID;

public record PepperDraftDto(
        UUID id,
        String prompt,
        String replicaId,
        PepperDraftStatus status,
        Boolean success,
        PepperPostDraftDto content,
        String errorMessage,
        Long publishedPostId,
        int version
) {
}
