package com.yoursay.user.user.dto;

import com.yoursay.user.user.AccountType;
import jakarta.validation.constraints.NotNull;

/** Complete, idempotent account update accepted by the administration API. */
public record AdminUserUpdateDto(
        @NotNull AccountType accountType,
        @NotNull Boolean active
) {
}
