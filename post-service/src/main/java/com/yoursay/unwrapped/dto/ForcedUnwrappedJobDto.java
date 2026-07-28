package com.yoursay.unwrapped.dto;

import java.util.UUID;

/** Durable generation job started explicitly by an administrator. */
public record ForcedUnwrappedJobDto(
        UUID jobId,
        Long postId,
        Integer milestone,
        String status,
        boolean created
) {
}
