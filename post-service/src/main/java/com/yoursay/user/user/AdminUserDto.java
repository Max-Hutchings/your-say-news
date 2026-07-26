package com.yoursay.user.user;

import java.time.LocalDate;

/**
 * PII-minimal account view for the protected administration workspace.
 *
 * Date of birth, consent data and characteristic answers are intentionally excluded.
 */
public record AdminUserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        LocalDate createdDate,
        boolean active,
        AccountType accountType
) {
}
