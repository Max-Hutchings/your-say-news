package com.yoursay.user.user.dto;

import com.yoursay.user.user.AccountType;
import jakarta.validation.constraints.NotNull;

/**
 * Idempotent account update accepted by the administration API. {@code publishingEnabled} is
 * optional: when omitted, a changed account type takes its default (on for OFFICIAL, off otherwise)
 * and an unchanged type keeps its current setting (ADR-066).
 */
public record AdminUserUpdateDto(
        @NotNull AccountType accountType,
        @NotNull Boolean active,
        Boolean publishingEnabled
) {
}
