package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.postagent.PepperDraftStatus;

import java.util.UUID;

/** Minimal trusted draft status exposed to the AutoPost workflow. */
public record AutoPostAgentDraftDto(
        UUID id,
        PepperDraftStatus status,
        Boolean success,
        PepperPostDraftDto content,
        String errorCode,
        int version
) {
}
