package com.yoursay.unwrapped.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectStoryRequest(@NotBlank @Size(max = 512) String reason) {
}
