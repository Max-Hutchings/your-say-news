package com.yoursay.unwrapped;

import com.yoursay.unwrapped.dto.ReviewStoryDto;
import com.yoursay.unwrapped.dto.UnwrappedAdminPostDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationTriggerDto;
import com.yoursay.unwrapped.dto.UnwrappedGenerationMonitorDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkPromptDto;
import com.yoursay.unwrapped.dto.UnwrappedBenchmarkResponseDto;

import com.yoursay.unwrapped.dto.UnwrappedResponseDto;

import com.yoursay.unwrapped.dto.FollowUpResponseDto;
import io.smallrye.mutiny.Uni;

import java.util.List;
import java.util.UUID;

/**
 * Application boundary for delivering, reviewing, and recording responses to Post Unwrapped
 * stories.
 *
 * <p>Only the Unwrapped REST controllers use this interface:</p>
 * <ul>
 *     <li>{@link UnwrappedAdminController} exposes the administrator review API under
     *     {@code /api/admin/unwrapped}.</li>
 *     <li>{@link UnwrappedController} exposes the voter journey under
 *     {@code /posts/{postId}/unwrapped}.</li>
 * </ul>
 *
 * <p>The methods participate in the journey in this order:</p>
 * <ol>
 *     <li>An administrator explicitly calls {@link #triggerGeneration(Long)}. Only that action
 *     places a post into background reconciliation and generation.</li>
 *     <li>The admin API calls {@link #reviewQueue()} to find drafts and
 *     {@link #reviewStory(UUID)} to inspect one.</li>
 *     <li>The admin API calls either {@link #approve(UUID, String)} or
 *     {@link #reject(UUID, String, String)}. Only approval makes a story eligible for voters.</li>
 *     <li>After casting a canonical vote, the voter API calls
 *     {@link #get(Long, String, String)}. It returns the newest eligible approved story, or an
 *     availability state when none can yet be served.</li>
 *     <li>After the voter reads the story, the voter API calls
 *     {@link #followUp(Long, UUID, Long, String, String)} once to record whether their preferred
 *     option changed. This never alters their canonical vote.</li>
 * </ol>
 *
 * <p>No other domain currently imports this interface. Casting a vote never queues Unwrapped
 * generation.</p>
 */
public interface UnwrappedService {
    /**
     * Returns the newest approved story eligible for the post's current canonical vote count.
     *
     * <p>The caller must be authenticated, must have cast a canonical vote on the post, and must
     * be allowed to view its results. Before the first observed-analysis milestone, or while an
     * eligible story is being generated or reviewed, the response contains an availability state
     * and notice rather than an unapproved story.</p>
     *
     * @param postId post whose approved Unwrapped story is requested
     * @param callerEmail authenticated caller's canonical email
     * @param authorization caller's authorization header forwarded to the votes boundary
     * @return delivery state, caller vote context, and the approved story when available
     */
    UnwrappedResponseDto get(Long postId, String callerEmail, String authorization);

    /**
     * Records whether the approved story changed the caller's preferred voting option.
     *
     * <p>The response is stored separately from the canonical vote and never changes published
     * aggregates. Submission is idempotent per caller and post: when a response already exists,
     * that original response is returned. The story must be approved for the supplied post and the
     * selected option must belong to that post.</p>
     *
     * @param postId post on which the caller originally voted
     * @param storyId exact approved story shown to the caller
     * @param optionId option selected after reading the story
     * @param callerEmail authenticated caller's canonical email
     * @param authorization caller's authorization header forwarded to the votes boundary
     * @return the newly stored or previously existing follow-up response
     */
    FollowUpResponseDto followUp(Long postId, UUID storyId, Long optionId,
                                 String callerEmail, String authorization);

    /**
     * Explicitly requests milestone reconciliation for one post.
     *
     * <p>This administrator-only path is the sole production entry point into generation. It does
     * not bypass milestone eligibility. The reconciliation worker counts committed votes and
     * idempotently creates the current milestone job only after this request.</p>
     *
     * @param postId post whose administrator-requested Unwrapped reconciliation should run
     * @return acknowledgement that reconciliation was queued
     */
    UnwrappedGenerationTriggerDto triggerGeneration(Long postId);

    /** Returns the current production system message for the benchmark editors. */
    UnwrappedBenchmarkPromptDto benchmarkPrompt();

    /** Generates up to three ephemeral prompt variants without creating queued or reviewable work. */
    UnwrappedBenchmarkResponseDto generateBenchmark(Long postId, List<String> systemPrompts);

    /**
     * Lists recent posts with identity-free overall vote totals for the administrator analysis
     * desk. No raw vote or voter characteristic leaves the votes boundary.
     *
     * @param page zero-based recent-post page
     * @param size requested page size, capped by the posts domain
     * @return recent post context and exact aggregate vote splits
     */
    Uni<List<UnwrappedAdminPostDto>> analysisPosts(int page, int size);

    /** Returns worker availability and persistent progress for active and completed generation jobs. */
    UnwrappedGenerationMonitorDto generationMonitor();

    /**
     * Lists generated stories currently awaiting human review, oldest first.
     *
     * @return immutable review DTOs for all stories in the draft state
     */
    List<ReviewStoryDto> reviewQueue();

    /**
     * Returns one generated story for human review regardless of its current review state.
     *
     * @param storyId story to inspect
     * @return complete review content and audit state
     */
    ReviewStoryDto reviewStory(UUID storyId);

    /**
     * Approves a draft story, making it eligible for voter delivery.
     *
     * @param storyId draft story to approve
     * @param reviewerEmail authenticated administrator's canonical email
     * @return story with its updated approval state
     */
    ReviewStoryDto approve(UUID storyId, String reviewerEmail);

    /**
     * Rejects a draft story with an internal review reason.
     *
     * @param storyId draft story to reject
     * @param reviewerEmail authenticated administrator's canonical email
     * @param reason non-blank internal reason for rejection
     * @return story with its updated rejection state
     */
    ReviewStoryDto reject(UUID storyId, String reviewerEmail, String reason);
}
