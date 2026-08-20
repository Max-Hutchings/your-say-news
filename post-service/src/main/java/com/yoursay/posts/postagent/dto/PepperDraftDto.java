package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.postagent.PepperDraftStatus;

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
