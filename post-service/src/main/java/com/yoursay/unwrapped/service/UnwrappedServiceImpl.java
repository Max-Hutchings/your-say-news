package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.PostService;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.unwrapped.dto.FollowUpResponseDto;
import com.yoursay.unwrapped.dto.ReviewStoryDto;
import com.yoursay.unwrapped.UnwrappedAvailabilityState;
import com.yoursay.unwrapped.UnwrappedMilestoneService;
import com.yoursay.unwrapped.dto.UnwrappedGenerationTriggerDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationMonitorDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationState;
import com.yoursay.unwrapped.dto.UnwrappedGenerationStatusDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkPromptDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkResponseDto;
import com.yoursay.unwrapped.dto.UnwrappedAdminPostDto;
import com.yoursay.unwrapped.dto.UnwrappedAdminVoteOptionDto;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedResponseDto;
import com.yoursay.unwrapped.UnwrappedService;
import com.yoursay.unwrapped.dto.UnwrappedStoryDto;
import com.yoursay.unwrapped.error.UnwrappedApiException;
import com.yoursay.unwrapped.agent.UnwrappedSystemPrompt;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJobRepository;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJob;
import com.yoursay.unwrapped.model.UnwrappedJobStatus;
import com.yoursay.unwrapped.model.UnwrappedFollowUp;
import com.yoursay.unwrapped.model.UnwrappedFollowUpRepository;
import com.yoursay.unwrapped.model.UnwrappedReviewStatus;
import com.yoursay.unwrapped.model.UnwrappedStory;
import com.yoursay.unwrapped.model.UnwrappedStoryRepository;
import com.yoursay.user.user.dto.UserAccessDto;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.votes.dto.VoteResponseDto;
import com.yoursay.votes.dto.PostAnalysisAggregateV1;
import com.yoursay.votes.dto.OverallOptionStatisticV1;
import com.yoursay.votes.PostAnalysisAggregateService;
import com.yoursay.votes.VoteService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class UnwrappedServiceImpl implements UnwrappedService {
    private static final String OBSERVED_NOTICE =
            "This analysis describes people who voted on this post; it is not a population survey.";
    private static final long MINIMUM_OBSERVED_VOTES = 100;
    private static final String API_KEY_NOT_CONFIGURED = "__not_configured__";

    @Inject
    VoteService voteService;
    @Inject
    PostVotingConfigurationService postService;
    @Inject
    PostService postReader;
    @Inject
    PostAnalysisAggregateService aggregateService;
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
    @Inject
    UnwrappedBenchmarkRunner benchmarkRunner;
    @Inject
    UnwrappedResearchPreparation researchPreparation;
    @Inject
    EntityManager entityManager;
    @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = API_KEY_NOT_CONFIGURED)
    String apiKey;

    @Override
    public UnwrappedResponseDto get(Long postId, String callerEmail, String authorization) {
        voteService.assertResultsUnlocked(postId, callerEmail, authorization);
        Long originalOptionId = voteService.getMyVote(postId, callerEmail, authorization)
                .orElseThrow()
                .optionId();
        Long existingFollowUp = followUpRepository.findByUserAndPost(userId(callerEmail), postId)
                .map(UnwrappedFollowUp::getFollowUpOptionId).orElse(null);

        long voteCount = voteService.countForPost(postId);
        Optional<UnwrappedStory> approved = storyRepository.newestApproved(postId, voteCount);
        Availability availability = approved
                .map(story -> availabilityOfApprovedStory(postId, story))
                .orElseGet(() -> availabilityWithoutApprovedStory(postId, voteCount));

        return new UnwrappedResponseDto(availability.state(), availability.notice(),
                originalOptionId, existingFollowUp,
                approved.map(this::toStory).orElse(null));
    }

    /**
     * A story stays visible while a newer milestone is being generated; the refreshing state only
     * tells the reader that a fresher version is on its way.
     */
    private Availability availabilityOfApprovedStory(Long postId, UnwrappedStory story) {
        boolean refreshing = jobRepository.count(
                "postId = ?1 and status in ('PENDING','GENERATING','DRAFT_READY') and createdAt > ?2",
                postId, story.getGeneratedAt()) > 0;
        return new Availability(refreshing
                ? UnwrappedAvailabilityState.REFRESHING
                : UnwrappedAvailabilityState.READY, OBSERVED_NOTICE);
    }

    /**
     * Explains why there is nothing to show yet: too few votes, no reliable demographic pattern, a
     * generation still running, or a generation that failed.
     */
    private Availability availabilityWithoutApprovedStory(Long postId, long voteCount) {
        if (voteCount < MINIMUM_OBSERVED_VOTES) {
            return new Availability(UnwrappedAvailabilityState.INSUFFICIENT_EVIDENCE,
                    "Post Unwrapped becomes available after 100 votes.");
        }
        // A running job outranks any earlier failure: the reader is told it is still being built.
        if (!hasJobInProgress(postId)) {
            if (failedForInsufficientEvidence(postId)) {
                return new Availability(UnwrappedAvailabilityState.INSUFFICIENT_EVIDENCE,
                        "There is no statistically reliable demographic pattern for every option yet.");
            }
            if (hasFailedJob(postId)) {
                return new Availability(UnwrappedAvailabilityState.FAILED,
                        "Pepper could not build this story yet.");
            }
        }
        return new Availability(UnwrappedAvailabilityState.BUILDING,
                "Pepper is building the context for every option.");
    }

    private boolean hasJobInProgress(Long postId) {
        return jobRepository.count(
                "postId = ?1 and status in ('PENDING','GENERATING','DRAFT_READY')", postId) > 0;
    }

    private boolean failedForInsufficientEvidence(Long postId) {
        return jobRepository.count(
                "postId = ?1 and status = 'FAILED' and errorCode = ?2", postId,
                "UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE") > 0;
    }

    private boolean hasFailedJob(Long postId) {
        return jobRepository.count("postId = ?1 and status = 'FAILED'", postId) > 0;
    }

    private record Availability(UnwrappedAvailabilityState state, String notice) {
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
        // Reusing the existing failed job keeps the attempt history for the milestone; only when
        // there is nothing to retry does the post go back through reconciliation.
        if (!retryFailedJobAtCurrentMilestone(postId)) {
            milestoneService.markForReconciliation(postId);
        }
        return new UnwrappedGenerationTriggerDto(postId, "RECONCILIATION_QUEUED");
    }

    private boolean retryFailedJobAtCurrentMilestone(Long postId) {
        Integer milestone = UnwrappedMilestones.highestReached(voteService.countForPost(postId));
        if (milestone == null) {
            return false;
        }
        Optional<UnwrappedAnalysisJob> failed = jobRepository.find(
                "postId = ?1 and milestone = ?2 and analysisVersion = ?3 and status = ?4",
                postId, milestone, UnwrappedStory.ANALYSIS_VERSION, UnwrappedJobStatus.FAILED)
                .firstResultOptional();
        failed.ifPresent(UnwrappedAnalysisJob::retryManually);
        return failed.isPresent();
    }

    @Override
    public UnwrappedBenchmarkPromptDto benchmarkPrompt(Long postId) {
        postService.findByPostId(postId)
                .orElseThrow(() -> UnwrappedApiException.postMissing(postId));
        var input = researchPreparation.prepare(postId).request();
        List<Long> optionIds = input.options().stream()
                .map(option -> option.option().id())
                .toList();
        return new UnwrappedBenchmarkPromptDto(
                UnwrappedSystemPrompt.DEFAULT,
                UnwrappedSystemPrompt.outputInstructions(optionIds),
                objectMapper.valueToTree(input));
    }

    @Override
    public UnwrappedBenchmarkResponseDto generateBenchmark(Long postId, List<String> systemPrompts) {
        PostVotingConfigurationDto post = postService.findByPostId(postId)
                .orElseThrow(() -> UnwrappedApiException.postMissing(postId));
        var variants = benchmarkRunner.run(postId, systemPrompts);
        return new UnwrappedBenchmarkResponseDto(
                postId, Instant.now(), post.options(), variants);
    }

    @Override
    public Uni<List<UnwrappedAdminPostDto>> analysisPosts(int page, int size) {
        return postReader.getRecent(page, size)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .map(posts -> posts.stream().map(this::toAdminPost).toList());
    }

    @Override
    public UnwrappedGenerationMonitorDto generationMonitor() {
        Map<Long, GenerationProgress> progress = countJobsByPost();
        addPendingReconciliations(progress);
        return new UnwrappedGenerationMonitorDto(workerAvailable(), Instant.now(),
                sortByMostRecentActivity(progress));
    }

    private Map<Long, GenerationProgress> countJobsByPost() {
        Map<Long, GenerationProgress> progress = new HashMap<>();
        for (UnwrappedAnalysisJob job : jobRepository.listAll()) {
            progress.computeIfAbsent(job.getPostId(), ignored -> new GenerationProgress()).add(job);
        }
        return progress;
    }

    /**
     * A post marked dirty has no job row yet, so it would otherwise be invisible to the monitor
     * between the vote that dirtied it and the scheduler enqueuing the work.
     */
    private void addPendingReconciliations(Map<Long, GenerationProgress> progress) {
        @SuppressWarnings("unchecked")
        List<Object[]> reconciliations = entityManager.createNativeQuery(
                "select post_id, dirty_at from unwrapped_reconciliation").getResultList();
        for (Object[] row : reconciliations) {
            progress.computeIfAbsent(((Number) row[0]).longValue(), ignored -> new GenerationProgress())
                    .addReconciliation((Instant) row[1]);
        }
    }

    private static List<UnwrappedGenerationStatusDto> sortByMostRecentActivity(
            Map<Long, GenerationProgress> progress) {
        return progress.entrySet().stream()
                .map(entry -> entry.getValue().toDto(entry.getKey()))
                .sorted((left, right) -> compareActivity(right.updatedAt(), left.updatedAt()))
                .toList();
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
        return new UnwrappedStoryDto(UnwrappedStory.STORY_SCHEMA_VERSION,
                story.getId(), story.getPostId(),
                story.getMilestone(), story.getCanonicalVoteCount(),
                story.getAggregateVersion(), story.getGeneratedAt(), story.getModel(),
                UnwrappedStoryResponseAssembler.argumentPages(draft),
                "Has seeing the context for every option changed your view?", post.options());
    }

    private ReviewStoryDto toReview(UnwrappedStory story) {
        PostVotingConfigurationDto post = postService.findByPostId(story.getPostId()).orElseThrow();
        return new ReviewStoryDto(story.getId(), story.getPostId(), story.getMilestone(),
                story.getCanonicalVoteCount(), story.getReviewStatus(),
                story.getGeneratedAt(), OBSERVED_NOTICE, post.options(),
                UnwrappedStoryResponseAssembler.argumentPages(draftJson(story)));
    }

    private UnwrappedAdminPostDto toAdminPost(PostDto post) {
        PostAnalysisAggregateV1 aggregate = aggregateService.capture(post.id());
        return new UnwrappedAdminPostDto(post.id(), post.summary(), post.supportQuestion(),
                post.caseFor(), post.caseAgainst(), post.jurisdiction(), post.votingType(),
                post.createdAt(), aggregate.canonicalVoteCount(),
                describeOptionsWithTotals(post, aggregate));
    }

    /** Every option of the post, including the ones nobody has voted for, which report zero. */
    private static List<UnwrappedAdminVoteOptionDto> describeOptionsWithTotals(
            PostDto post, PostAnalysisAggregateV1 aggregate) {
        Map<Long, OverallOptionStatisticV1> statisticsByOptionId = aggregate.overall().stream()
                .collect(Collectors.toMap(OverallOptionStatisticV1::optionId, Function.identity()));
        return post.voteOptions().stream()
                .map(option -> {
                    OverallOptionStatisticV1 statistic = statisticsByOptionId.get(option.id());
                    return new UnwrappedAdminVoteOptionDto(option.id(), option.label(), option.ordinal(),
                            option.semanticKey(), statistic == null ? 0 : statistic.count(),
                            statistic == null ? 0.0 : statistic.percentage());
                })
                .toList();
    }

    private UnwrappedResearchDraftV1 draftJson(UnwrappedStory story) {
        try {
            if (!UnwrappedStory.STORY_SCHEMA_VERSION.equals(story.getStorySchemaVersion())) {
                return LegacyUnwrappedDraftAdapter.convert(story.getStoryJson(), objectMapper);
            }
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

    private boolean workerAvailable() {
        return apiKey != null && !apiKey.isBlank() && !API_KEY_NOT_CONFIGURED.equals(apiKey);
    }

    private static int compareActivity(Instant left, Instant right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        return left.compareTo(right);
    }

    private static final class GenerationProgress {
        private int queued;
        private int generating;
        private int ready;
        private int failed;
        private Instant updatedAt;
        private UnwrappedAnalysisJob latestFinished;

        void add(UnwrappedAnalysisJob job) {
            switch (job.getStatus()) {
                case PENDING -> queued++;
                case GENERATING -> generating++;
                case DRAFT_READY -> ready++;
                case FAILED -> failed++;
            }
            Instant activity = job.lastActivityAt();
            if (updatedAt == null || activity.isAfter(updatedAt)) updatedAt = activity;
            if ((job.getStatus() == UnwrappedJobStatus.DRAFT_READY
                    || job.getStatus() == UnwrappedJobStatus.FAILED)
                    && (latestFinished == null
                    || activity.isAfter(latestFinished.lastActivityAt()))) {
                latestFinished = job;
            }
        }

        void addReconciliation(Instant dirtyAt) {
            queued++;
            if (updatedAt == null || dirtyAt.isAfter(updatedAt)) updatedAt = dirtyAt;
        }

        UnwrappedGenerationStatusDto toDto(Long postId) {
            UnwrappedGenerationState state;
            if (generating > 0) {
                state = UnwrappedGenerationState.GENERATING;
            } else if (queued > 0) {
                state = UnwrappedGenerationState.QUEUED;
            } else if (latestFinished != null
                    && latestFinished.getStatus() == UnwrappedJobStatus.FAILED) {
                state = UnwrappedGenerationState.FAILED;
            } else if (ready > 0) {
                state = UnwrappedGenerationState.READY_FOR_REVIEW;
            } else {
                state = UnwrappedGenerationState.NOT_STARTED;
            }
            String error = state == UnwrappedGenerationState.FAILED
                    ? latestFinished.getErrorMessage() : null;
            return new UnwrappedGenerationStatusDto(postId, state, queued, generating, ready,
                    failed, updatedAt, error);
        }
    }

}
