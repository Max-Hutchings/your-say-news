package com.yoursay.user.user.dto;

import com.yoursay.user.user.AccountType;

/**
 * PII-free access view for the authenticated account. Other services use this instead of trusting
 * an email, account id or capability supplied by the client.
 */
public record UserAccessDto(
        Long userId,
        AccountType accountType,
        boolean canPublish
) {
}
