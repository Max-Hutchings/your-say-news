package com.yoursay.posts.postagent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePepperDraftRequest(
        @Min(1) int version,
        @NotNull @Valid PepperPostDraftDto content
) {
}
