package com.yoursay.user.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSessionRequest(@NotBlank String idToken) {
}
