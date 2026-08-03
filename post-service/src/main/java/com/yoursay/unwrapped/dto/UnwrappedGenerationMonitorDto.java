package com.yoursay.unwrapped.dto;

import java.time.Instant;
import java.util.List;

/** Health and live job progress for the administrator Unwrapped generation desk. */
public record UnwrappedGenerationMonitorDto(
        boolean workerAvailable,
        Instant refreshedAt,
        List<UnwrappedGenerationStatusDto> statuses
) {
}
