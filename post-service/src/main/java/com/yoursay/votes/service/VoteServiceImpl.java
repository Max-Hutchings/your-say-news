package com.yoursay.votes.service;

import com.yoursay.votes.CharacteristicSnapshot;
import com.yoursay.votes.VoteResponseDto;
import com.yoursay.votes.VoteService;
import com.yoursay.votes.client.UserCharacteristicClient;
import com.yoursay.votes.client.UserCharacteristicView;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Optional;

@ApplicationScoped
public class VoteServiceImpl implements VoteService {

    @Inject
    VoteRepository voteRepository;

    @RestClient
    UserCharacteristicClient userClient;

    @Override
    @Transactional
    public VoteResponseDto castVote(Long postId, boolean voteFor, String callerEmail, String authorization) {
        // 1. Resolve the numeric user id from user-service (role-gated, so bearer is forwarded).
        Long userId = resolveUserId(callerEmail, authorization);

        // 2. Enforce one-vote-per-user: 409 if already voted.
        if (voteRepository.existsByPostAndUser(postId, userId)) {
            Log.infof("Duplicate vote rejected: user %d already voted on post %d", userId, postId);
            throw new ClientErrorException("User has already voted on this post", Response.Status.CONFLICT);
        }

        // 3. Capture a point-in-time characteristic snapshot (null-safe: empty snapshot if no profile).
        CharacteristicSnapshot snapshot = fetchSnapshot(authorization);

        // 4. Persist and return the PII-safe response.
        Vote vote = new Vote(postId, userId, voteFor, snapshot);
        voteRepository.persist(vote);
        Log.infof("Vote persisted: id=%s postId=%d voteFor=%b", vote.getId(), postId, voteFor);
        return toResponse(vote);
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

    // ── private helpers ──────────────────────────────────────────────────────

    private Long resolveUserId(String callerEmail, String authorization) {
        Response resp = userClient.getUserByEmail(callerEmail, authorization);
        if (resp.getStatus() == Response.Status.NO_CONTENT.getStatusCode()
                || resp.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
            throw new WebApplicationException("No user account for authenticated caller", 401);
        }
        if (resp.getStatus() >= 400) {
            throw new WebApplicationException("Could not resolve user id", resp.getStatus());
        }
        UserCharacteristicClient.UserRef ref = resp.readEntity(UserCharacteristicClient.UserRef.class);
        if (ref == null || ref.id() == null) {
            throw new WebApplicationException("No user account for authenticated caller", 401);
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
                Log.warnf("Characteristic lookup returned %d; using empty snapshot", resp.getStatus());
                return CharacteristicSnapshot.empty();
            }
            UserCharacteristicView view = resp.readEntity(UserCharacteristicView.class);
            return CharacteristicSnapshotMapper.from(view);
        } catch (Exception e) {
            // Non-critical: a missing snapshot degrades to UNKNOWN buckets in aggregation.
            Log.warnf("Failed to fetch characteristics: %s — using empty snapshot", e.getMessage());
            return CharacteristicSnapshot.empty();
        }
    }

    private static VoteResponseDto toResponse(Vote vote) {
        return new VoteResponseDto(vote.getId(), vote.getPostId(), vote.isVoteFor());
    }
}
