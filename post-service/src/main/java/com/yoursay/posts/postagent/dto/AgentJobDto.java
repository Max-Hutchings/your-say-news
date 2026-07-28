package com.yoursay.posts.postagent.dto;

import com.yoursay.posts.postagent.AgentJobStatus;

import java.time.Instant;
import java.util.UUID;

public record AgentJobDto(
        UUID id,
        AgentJobStatus status,
        int attemptCount,
        String model,
        AgentDraftDto draft,
        String errorCode,
        String errorMessage,
        Long publishedPostId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
