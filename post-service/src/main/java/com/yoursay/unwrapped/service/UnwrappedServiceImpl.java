package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.unwrapped.dto.FollowUpResponseDto;
import com.yoursay.unwrapped.dto.ReviewStoryDto;
import com.yoursay.unwrapped.UnwrappedAvailabilityState;
import com.yoursay.unwrapped.UnwrappedMilestoneService;
import com.yoursay.unwrapped.dto.UnwrappedGenerationTriggerDto;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedResponseDto;
import com.yoursay.unwrapped.UnwrappedService;
import com.yoursay.unwrapped.dto.UnwrappedStoryDto;
import com.yoursay.unwrapped.error.UnwrappedApiException;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJobRepository;
import com.yoursay.unwrapped.model.UnwrappedFollowUp;
import com.yoursay.unwrapped.model.UnwrappedFollowUpRepository;
import com.yoursay.unwrapped.model.UnwrappedReviewStatus;
import com.yoursay.unwrapped.model.UnwrappedStory;
import com.yoursay.unwrapped.model.UnwrappedStoryRepository;
import com.yoursay.user.user.dto.UserAccessDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.votes.dto.VoteResponseDto;
import com.yoursay.votes.VoteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UnwrappedServiceImpl implements UnwrappedService {
    private static final long MINIMUM_OBSERVED_VOTES = 100;

    @Inject
    VoteService voteService;
    @Inject
    PostVotingConfigurationService postService;
    @Inject
    YourSayUserService userService;
    @Inject
    UnwrappedAnalysisJobRepository jobRepository;
    @Inject
    UnwrappedStoryRepository storyRepository;
    @Inject
    UnwrappedFollowUpRepository followUpRepository;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    UnwrappedMilestoneService milestoneService;

    @Override
    public UnwrappedResponseDto get(Long postId, String callerEmail, String authorization) {
        voteService.assertResultsUnlocked(postId, callerEmail, authorization);
        VoteResponseDto original = voteService.getMyVote(postId, callerEmail, authorization)
                .orElseThrow();
        long count = voteService.countForPost(postId);
        Optional<UnwrappedStory> approved = storyRepository.newestApproved(postId, count);
        Long existingFollowUp = followUpRepository.findByUserAndPost(userId(callerEmail), postId)
                .map(UnwrappedFollowUp::getFollowUpOptionId).orElse(null);
        if (approved.isEmpty()) {
            if (count < MINIMUM_OBSERVED_VOTES) {
                return new UnwrappedResponseDto(
                        UnwrappedAvailabilityState.INSUFFICIENT_EVIDENCE,
                        "Post Unwrapped becomes available after 100 votes.",
                        original.optionId(), existingFollowUp, null);
            }
            boolean failed = jobRepository.count("postId = ?1 and status = 'FAILED'", postId) > 0
                    && jobRepository.count("postId = ?1 and status in ('PENDING','GENERATING','DRAFT_READY')",
                    postId) == 0;
            return new UnwrappedResponseDto(
                    failed ? UnwrappedAvailabilityState.FAILED : UnwrappedAvailabilityState.BUILDING,
                    failed ? "Pepper could not build this story yet."
                            : "Pepper is building the context for every option.",
                    original.optionId(), existingFollowUp, null);
        }
        UnwrappedStory story = approved.get();
        boolean refreshing = jobRepository.count(
                "postId = ?1 and status in ('PENDING','GENERATING','DRAFT_READY') and createdAt > ?2",
                postId, story.getGeneratedAt()) > 0;
        return new UnwrappedResponseDto(
                refreshing ? UnwrappedAvailabilityState.REFRESHING : UnwrappedAvailabilityState.READY,
                "This analysis describes people who voted on this post; it is not a population survey.",
                original.optionId(), existingFollowUp, toStory(story));
    }

    @Override
    @Transactional
    public FollowUpResponseDto followUp(Long postId, UUID storyId, Long optionId,
                                        String callerEmail, String authorization) {
        voteService.assertResultsUnlocked(postId, callerEmail, authorization);
        Long userId = userId(callerEmail);
        Optional<UnwrappedFollowUp> existing = followUpRepository.findByUserAndPost(userId, postId);
        if (existing.isPresent()) return toFollowUp(existing.get());
        UnwrappedStory story = storyRepository.findByIdOptional(storyId)
                .orElseThrow(() -> UnwrappedApiException.storyMissing(storyId));
        if (!postId.equals(story.getPostId())
                || story.getReviewStatus() != UnwrappedReviewStatus.APPROVED) {
            throw UnwrappedApiException.storyNotAvailable(storyId);
        }
        PostVotingConfigurationDto post = postService.findByPostId(postId)
                .orElseThrow(() -> UnwrappedApiException.optionNotAvailable(optionId));
        if (!post.containsOption(optionId)) throw UnwrappedApiException.optionNotAvailable(optionId);
        Long originalOption = voteService.getMyVote(postId, callerEmail, authorization)
                .map(VoteResponseDto::optionId).orElseThrow();
        UnwrappedFollowUp followUp =
                new UnwrappedFollowUp(userId, postId, storyId, originalOption, optionId);
        followUpRepository.persistAndFlush(followUp);
        return toFollowUp(followUp);
    }

    @Override
    @Transactional
    public UnwrappedGenerationTriggerDto triggerGeneration(Long postId) {
        postService.findByPostId(postId)
                .orElseThrow(() -> UnwrappedApiException.postMissing(postId));
        milestoneService.markForReconciliation(postId);
        return new UnwrappedGenerationTriggerDto(postId, "RECONCILIATION_QUEUED");
    }

    @Override
    public List<ReviewStoryDto> reviewQueue() {
        return storyRepository.drafts().stream().map(this::toReview).toList();
    }

    @Override
    public ReviewStoryDto reviewStory(UUID storyId) {
        return toReview(storyRepository.findByIdOptional(storyId)
                .orElseThrow(() -> UnwrappedApiException.storyMissing(storyId)));
    }

    @Override
    @Transactional
    public ReviewStoryDto approve(UUID storyId, String reviewerEmail) {
        UnwrappedStory story = draft(storyId);
        story.approve(userId(reviewerEmail));
        return toReview(story);
    }

    @Override
    @Transactional
    public ReviewStoryDto reject(UUID storyId, String reviewerEmail, String reason) {
        UnwrappedStory story = draft(storyId);
        story.reject(userId(reviewerEmail), reason.trim());
        return toReview(story);
    }

    private UnwrappedStory draft(UUID storyId) {
        UnwrappedStory story = storyRepository.findByIdOptional(storyId)
                .orElseThrow(() -> UnwrappedApiException.storyMissing(storyId));
        if (story.getReviewStatus() != UnwrappedReviewStatus.DRAFT) {
            throw UnwrappedApiException.invalidReviewState(storyId);
        }
        return story;
    }

    private Long userId(String email) {
        UserAccessDto access = userService.getAccessByEmail(email);
        if (access == null || access.userId() == null) throw UnwrappedApiException.userMissing();
        return access.userId();
    }

    UnwrappedStoryDto toStory(UnwrappedStory story) {
        UnwrappedResearchDraftV1 draft = draftJson(story);
        PostVotingConfigurationDto post = postService.findByPostId(story.getPostId()).orElseThrow();
        return new UnwrappedStoryDto("unwrapped-story-v1", story.getId(), story.getPostId(),
                story.getMilestone(), story.getCanonicalVoteCount(),
                story.getAggregateVersion(), story.getGeneratedAt(), story.getModel(),
                UnwrappedStoryResponseAssembler.argumentPages(draft),
                "Has seeing the context for every option changed your view?", post.options());
    }

    private ReviewStoryDto toReview(UnwrappedStory story) {
        return new ReviewStoryDto(story.getId(), story.getPostId(), story.getMilestone(),
                story.getCanonicalVoteCount(), story.getReviewStatus(),
                story.getGeneratedAt(), draftJson(story));
    }

    private UnwrappedResearchDraftV1 draftJson(UnwrappedStory story) {
        try {
            return objectMapper.treeToValue(story.getStoryJson(), UnwrappedResearchDraftV1.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored Unwrapped story is invalid: " + story.getId(), e);
        }
    }

    private static FollowUpResponseDto toFollowUp(UnwrappedFollowUp value) {
        return new FollowUpResponseDto(value.getId(), value.getPostId(), value.getStoryId(),
                value.getOriginalOptionId(), value.getFollowUpOptionId(),
                !value.getOriginalOptionId().equals(value.getFollowUpOptionId()), value.getCreatedAt());
    }

}
