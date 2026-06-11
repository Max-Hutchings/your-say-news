package com.yoursay.user;

import java.time.LocalDate;

/**
 * The user view that crosses the domain boundary (HTTP responses and cross-domain calls).
 */
public record YourSayUserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        LocalDate createdDate,
        boolean active
) {
}
