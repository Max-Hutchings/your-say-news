package com.yoursay.unwrapped.dto;

import com.yoursay.unwrapped.UnwrappedAvailabilityState;

public record UnwrappedResponseDto(
        UnwrappedAvailabilityState state,
        String notice,
        Long originalOptionId,
        Long existingFollowUpOptionId,
        UnwrappedStoryDto story
) {
}
