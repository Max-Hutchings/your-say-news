package com.yoursay.platform.mobiletelemetry.dto;

import jakarta.validation.constraints.Size;

/** The device that sent a batch. Coarse on purpose: no device model, locale or identifier. */
public record MobileClientDto(
        @Size(max = 16) String platform,
        @Size(max = 32) String osVersion,
        @Size(max = 32) String appVersion
) {
}
