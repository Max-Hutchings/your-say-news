package com.yoursay.posts.postagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateAgentPostRequest(
        @NotBlank
        @Size(max = 2000)
        String request
) {
}
