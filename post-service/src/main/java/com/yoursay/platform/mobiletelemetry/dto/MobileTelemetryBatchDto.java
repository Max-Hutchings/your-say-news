package com.yoursay.platform.mobiletelemetry.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Events the app buffered since its last upload. {@code sessionId} is random per app launch and is
 * never linked to the account, so a journey can be followed without naming the person on it.
 */
public record MobileTelemetryBatchDto(
        @NotNull @Pattern(regexp = "[0-9a-f]{32}") String sessionId,
        @NotNull @Valid MobileClientDto client,
        @NotNull @Size(max = MobileTelemetryBatchDto.MAX_EVENTS) List<@NotNull @Valid MobileTelemetryEventDto> events
) {
    public static final int MAX_EVENTS = 200;
}
