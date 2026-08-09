package com.yoursay.unwrapped.dto;

import jakarta.validation.constraints.NotNull;

public record FollowUpRequest(@NotNull Long optionId) {
}
