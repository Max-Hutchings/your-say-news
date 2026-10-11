package com.yoursay.user.auth.dto;

/**
 * Public Firebase web settings for the admin SPA. None of these are secrets: Firebase web API keys
 * identify the project, and access is enforced by the backend's database admin check.
 * {@code authEmulatorUrl} is only set on a laptop.
 */
public record AdminWebConfigDto(
        String projectId,
        String authDomain,
        String apiKey,
        String appId,
        AdminSignInMethod signInMethod,
        String authEmulatorUrl
) {
}
