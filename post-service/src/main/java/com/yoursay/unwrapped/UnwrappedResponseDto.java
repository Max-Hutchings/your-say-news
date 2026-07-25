package com.yoursay.unwrapped;

public record UnwrappedResponseDto(
        UnwrappedAvailabilityState state,
        String notice,
        Long originalOptionId,
        Long existingFollowUpOptionId,
        UnwrappedStoryDto story
) {
}
