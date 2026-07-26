package com.yoursay.user.user;

import jakarta.validation.constraints.NotNull;

/** Complete, idempotent account update accepted by the administration API. */
public record AdminUserUpdateDto(
        @NotNull AccountType accountType,
        @NotNull Boolean active
) {
}
