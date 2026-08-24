package com.yoursay.votes.service;

import com.yoursay.votes.dto.CharacteristicSnapshot;
import com.yoursay.votes.dto.VoteResponseDto;
import com.yoursay.votes.VoteService;
import com.yoursay.votes.client.UserCharacteristicClient;
import com.yoursay.votes.client.UserCharacteristicView;
import com.yoursay.votes.error.VoteApiException;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.function.Predicate;

@ApplicationScoped
public class VoteServiceImpl implements VoteService {

    @Inject
    VoteRepository voteRepository;

    @Inject
    UserCharacteristicClient userClient;

    @Inject
    DomainMetrics metrics;

    @Inject
    PostVotingConfigurationService votingConfigurationService;

    @Override
    public void assertVotableSelection(Long postId, Long optionId) {
        if (postId == null) {
            throw VoteApiException.invalidVote("postId is required");
        }
        if (optionId == null) {
            throw VoteApiException.invalidVote("optionId is required");
        }
        PostVotingConfigurationDto configuration = votingConfigurationService.findByPostId(postId)
                .orElseThrow(() -> VoteApiException.postMissing(postId));
        if (!configuration.containsOption(optionId)) {
            throw VoteApiException.optionNotAvailable(postId, optionId);
        }
    }

    @Override
    @Transactional
    public VoteResponseDto castVote(Long postId, Long optionId, String callerEmail, String authorization) {
        try {
            assertVotableSelection(postId, optionId);
            Long userId = resolveUserId(callerEmail, authorization);
            assertHasNotVoted(postId, userId);
            // A point-in-time characteristic snapshot is what makes the vote aggregatable later.
            CharacteristicSnapshot snapshot = fetchSnapshot(authorization);

            Vote vote = persistVote(new Vote(postId, userId, optionId, snapshot));
            Log.infof("Canonical vote persisted: id=%s postId=%d", vote.getId(), postId);
            recordMetric("castVote", true);
            return toResponse(vote);
        } catch (RuntimeException e) {
            recordMetric("castVote", false);
            throw e;
        }
    }

    /** One vote per user per post: a second attempt is a 409 rather than a silent overwrite. */
    private void assertHasNotVoted(Long postId, Long userId) {
        if (voteRepository.existsByPostAndUser(postId, userId)) {
            Log.infof("Duplicate vote rejected: user %d already voted on post %d", userId, postId);
            throw VoteApiException.duplicateVote(postId, userId);
        }
    }

    /**
     * Persist and flush together so a foreign-key (unknown post/option) or unique (duplicate)
     * violation is raised here and translated into the precise 404/409, rather than escaping at
     * commit time as a generic 500.
     */
    private Vote persistVote(Vote vote) {
        try {
            voteRepository.persist(vote);
            voteRepository.flush();
            return vote;
        } catch (RuntimeException e) {
            throw translatePersistenceFailure(e, vote);
        }
    }

    private static RuntimeException translatePersistenceFailure(RuntimeException failure, Vote vote) {
        if (causeChainMentions(failure, "fk_votes_option_post")) {
            return VoteApiException.optionNotAvailable(vote.getPostId(), vote.getOptionId());
        }
        if (isMissingPostPersistenceFailure(failure)) {
            return VoteApiException.postMissing(vote.getPostId());
        }
        if (isDuplicateVotePersistenceFailure(failure)) {
            return VoteApiException.duplicateVote(vote.getPostId(), vote.getUserId());
        }
        return failure;
    }

    @Override
    public Optional<VoteResponseDto> getMyVote(Long postId, String callerEmail, String authorization) {
        Long userId = resolveUserId(callerEmail, authorization);
        return voteRepository.findByPostAndUser(postId, userId).map(VoteServiceImpl::toResponse);
    }

    @Override
    public long countForPost(Long postId) {
        return voteRepository.count("postId", postId);
    }

    @Override
    public void assertResultsUnlocked(Long postId, String callerEmail, String authorization) {
        // 404 first: an unknown post is disclosed as not-found before we reveal anything vote-related.
        if (!voteRepository.postExists(postId)) {
            throw VoteApiException.postMissing(postId);
        }
        // 403: results stay locked until the caller has cast their own vote on this post.
        if (getMyVote(postId, callerEmail, authorization).isEmpty()) {
            throw VoteApiException.resultsLocked(postId);
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private Long resolveUserId(String callerEmail, String authorization) {
        Response resp = userClient.getUserByEmail(callerEmail, authorization);
        if (resp.getStatus() == Response.Status.NO_CONTENT.getStatusCode()
                || resp.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            throw VoteApiException.userMissing(callerEmail);
        }
        if (resp.getStatus() >= 400) {
            throw VoteApiException.userLookupFailed(callerEmail, resp.getStatus());
        }
        UserCharacteristicClient.UserRef ref = resp.readEntity(UserCharacteristicClient.UserRef.class);
        if (ref == null || ref.id() == null) {
            throw VoteApiException.userMissing(callerEmail);
        }
        return ref.id();
    }

    private CharacteristicSnapshot fetchSnapshot(String authorization) {
        try {
            Response resp = userClient.getMyCharacteristics(authorization);
            if (resp.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
                // User has not completed characteristic onboarding — empty snapshot is fine.
                return CharacteristicSnapshot.empty();
            }
            if (resp.getStatus() >= 400) {
                Log.warnf("Characteristic lookup failed for vote snapshot: status=%d; using empty snapshot",
                        resp.getStatus());
                return CharacteristicSnapshot.empty();
            }
            UserCharacteristicView view = resp.readEntity(UserCharacteristicView.class);
            return CharacteristicSnapshotMapper.from(view);
        } catch (Exception e) {
            // Non-critical: a missing snapshot degrades to UNKNOWN buckets in aggregation.
            Log.warnf(e, "Failed to fetch characteristics for vote snapshot; using empty snapshot: %s",
                    e.getMessage());
            return CharacteristicSnapshot.empty();
        }
    }

    private static VoteResponseDto toResponse(Vote vote) {
        return new VoteResponseDto(vote.getId(), vote.getPostId(), vote.getOptionId());
    }

    private void recordMetric(String operation, boolean success) {
        if (metrics != null) {
            metrics.recordOperation("votes", operation, success);
        }
    }

    private static boolean isMissingPostPersistenceFailure(Throwable error) {
        return anyCauseMatches(error, description -> description.contains("fk_votes_post")
                || (description.contains("foreign key") && description.contains("post")));
    }

    private static boolean isDuplicateVotePersistenceFailure(Throwable error) {
        return anyCauseMatches(error, description -> description.contains("uk_votes_post_user")
                || (description.contains("duplicate") && description.contains("vote"))
                || (description.contains("unique") && description.contains("vote")));
    }

    private static boolean causeChainMentions(Throwable error, String marker) {
        return anyCauseMatches(error, description -> description.contains(marker));
    }

    /**
     * The driver reports constraint violations only in the exception text, so the whole cause chain
     * is searched by its lower-cased message and type name.
     */
    private static boolean anyCauseMatches(Throwable error, Predicate<String> matcher) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage() == null ? "" : current.getMessage();
            if (matcher.test((message + " " + current.getClass().getName()).toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
