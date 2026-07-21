package com.yoursay.user;

/**
 * PII-free access view for the authenticated account. Other services use this instead of trusting
 * an email, account id or capability supplied by the client.
 */
public record UserAccessDto(
        Long userId,
        AccountType accountType,
        PublisherStatus publisherStatus,
        boolean canPublish
) {
}
