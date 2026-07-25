package com.yoursay.unwrapped;

import java.util.List;
import java.util.UUID;

public interface UnwrappedService {
    UnwrappedResponseDto get(Long postId, String callerEmail, String authorization);
    FollowUpResponseDto followUp(Long postId, UUID storyId, Long optionId,
                                 String callerEmail, String authorization);
    UUID enqueuePrediction(Long postId);
    List<ReviewStoryDto> reviewQueue();
    ReviewStoryDto reviewStory(UUID storyId);
    ReviewStoryDto approve(UUID storyId, String reviewerEmail);
    ReviewStoryDto reject(UUID storyId, String reviewerEmail, String reason);
}
