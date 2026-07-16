package com.yoursay.user;

/**
 * PII-free view of a user that crosses the domain/service boundary for lookups by id or email.
 * Carries only the anonymised {@code id} — never email, name or date of birth — so an authenticated
 * caller cannot harvest the user base's identity by iterating these endpoints. Cross-service clients
 * only ever read the id, so this is all they need.
 */
public record UserRefDto(Long id) {
}
