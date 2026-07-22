package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.VoteResponseDto;
import com.yoursay.votes.VoteService;
import com.yoursay.votes.client.UserCharacteristicClient;
import com.yoursay.votes.client.UserCharacteristicView;
import com.yoursay.votes.error.VoteApiException;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import com.yoursay.observability.DomainMetrics;
import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class VoteServiceImpl implements VoteService {

    @Inject
    VoteRepository voteRepository;

    @RestClient
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
            // 1. Resolve the numeric user id from user-service (role-gated, so bearer is forwarded).
            Long userId = resolveUserId(callerEmail, authorization);

            // 2. Enforce one-vote-per-user: 409 if already voted.
            if (voteRepository.existsByPostAndUser(postId, userId)) {
                Log.infof("Duplicate vote rejected: user %d already voted on post %d", userId, postId);
                throw VoteApiException.duplicateVote(postId, userId);
            }

            // 3. Capture a point-in-time characteristic snapshot (null-safe: empty snapshot if no profile).
            CharacteristicSnapshot snapshot = fetchSnapshot(authorization);

            // 4. Persist and return the PII-safe response.
            Vote vote = new Vote(postId, userId, optionId, snapshot);
            try {
                voteRepository.persist(vote);
                // Flush now so a foreign-key (unknown post) or unique (duplicate) violation is
                // thrown here — inside this try — rather than later at commit, where it would
                // escape as a generic 500 instead of the precise 404/409 below.
                voteRepository.flush();
            } catch (RuntimeException e) {
                if (isInvalidOptionPersistenceFailure(e)) {
                    throw VoteApiException.optionNotAvailable(postId, optionId);
                }
                if (isMissingPostPersistenceFailure(e)) {
                    throw VoteApiException.postMissing(postId);
                }
                if (isDuplicateVotePersistenceFailure(e)) {
                    throw VoteApiException.duplicateVote(postId, userId);
                }
                throw e;
            }
            Log.infof("Canonical vote persisted: id=%s postId=%d", vote.getId(), postId);
            recordMetric("castVote", true);
            return toResponse(vote);
        } catch (RuntimeException e) {
            recordMetric("castVote", false);
            throw e;
        }
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
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            String className = current.getClass().getName();
            String combined = ((message == null ? "" : message) + " " + className).toLowerCase();
            if (combined.contains("fk_votes_post")
                    || (combined.contains("foreign key") && combined.contains("post"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isDuplicateVotePersistenceFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            String className = current.getClass().getName();
            String combined = ((message == null ? "" : message) + " " + className).toLowerCase();
            if (combined.contains("uk_votes_post_user")
                    || (combined.contains("duplicate") && combined.contains("vote"))
                    || (combined.contains("unique") && combined.contains("vote"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isInvalidOptionPersistenceFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String combined = ((current.getMessage() == null ? "" : current.getMessage()) + " "
                    + current.getClass().getName()).toLowerCase();
            if (combined.contains("fk_votes_option_post")) return true;
            current = current.getCause();
        }
        return false;
    }
}
