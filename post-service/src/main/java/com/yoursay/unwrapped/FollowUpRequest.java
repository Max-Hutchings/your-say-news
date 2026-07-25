package com.yoursay.unwrapped;

import jakarta.validation.constraints.NotNull;

public record FollowUpRequest(@NotNull Long optionId) {
}
