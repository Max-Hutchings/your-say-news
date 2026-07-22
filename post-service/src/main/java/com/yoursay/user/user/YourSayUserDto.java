package com.yoursay.user.user;

import com.yoursay.user.user.AccountType;
import com.yoursay.user.user.PublisherStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The user view that crosses the domain boundary (HTTP responses and cross-domain calls).
 */
public record YourSayUserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String displayName,
        String handle,
        String avatarUrl,
        LocalDate dateOfBirth,
        LocalDate createdDate,
        boolean active,
        AccountType accountType,
        PublisherStatus publisherStatus,
        boolean canPublish,
        Instant consentedAt,
        String privacyPolicyVersion
) {
}
