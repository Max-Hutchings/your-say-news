package com.yoursay.votes;

import com.yoursay.posts.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.posts.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.client.UserCharacteristicClient;
import com.yoursay.votes.error.VoteApiException;
import com.yoursay.votes.model.Vote;
import com.yoursay.votes.model.VoteRepository;
import com.yoursay.votes.service.VoteServiceImpl;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for the one-vote-per-user enforcement logic in {@link VoteServiceImpl}. No Quarkus
 * context — just the service, mocked dependencies, and the contract: a second vote on the same
 * post by the same user must throw a 409, not silently overwrite or duplicate.
 */
@ExtendWith(MockitoExtension.class)
class VoteDuplicateEnforcementTest {

    @Mock
    VoteRepository voteRepository;

    @Mock
    UserCharacteristicClient userClient;

    @Mock
    PostVotingConfigurationService votingConfigurationService;

    @InjectMocks
    VoteServiceImpl voteService;

    private static final String VOTER_EMAIL = "voter@test.com";
    private static final String AUTH = "Bearer test-token";
    private static final long USER_ID = 10L;
    private static final long POST_ID = 5L;
    private static final long OPTION_ID = 51L;

    @BeforeEach
    void stubUserLookup() {
        // lenient: castVote_unknownEmail_throws401 and getMyVote_differentUser_queriesWithOwnUserId
        // override or don't use this stub, so strict mode would flag it without lenient().
        Response userRefResp = Response.ok(new UserCharacteristicClient.UserRef(USER_ID)).build();
        lenient().when(userClient.getUserByEmail(VOTER_EMAIL, AUTH)).thenReturn(userRefResp);
        lenient().when(votingConfigurationService.findByPostId(anyLong()))
                .thenAnswer(invocation -> Optional.of(configuration(invocation.getArgument(0))));
    }

    @Test
    void castVote_firstVote_accepted() {
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());
        // persist() is void — default mock does nothing, which is correct.

        VoteResponseDto result = voteService.castVote(POST_ID, OPTION_ID, VOTER_EMAIL, AUTH);

        assertEquals(POST_ID, result.postId());
        assertEquals(OPTION_ID, result.optionId());
        Vote persisted = capturePersistedVote();
        assertEquals(POST_ID, persisted.getPostId());
        assertEquals(USER_ID, persisted.getUserId());
        assertEquals(OPTION_ID, persisted.getOptionId());
        assertEquals(CharacteristicSnapshot.UNKNOWN, persisted.getSnapshot().bucketFor("ageRange"));
    }

    @Test
    void castVote_secondVote_samePoster_throws409() {
        // The duplicate-vote guard fires before any persist call.
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(true);

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.castVote(POST_ID, OPTION_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(Response.Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
        assertEquals("VOTE_DUPLICATE", ex.errorCode());
        verify(voteRepository, never()).persist(any(com.yoursay.votes.model.Vote.class));
    }

    @Test
    void castVote_duplicateRaceOnPersist_throws409() {
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());
        doThrow(new RuntimeException("duplicate key value violates unique constraint \"uk_votes_post_user\""))
                .when(voteRepository).persist(any(Vote.class));

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.castVote(POST_ID, OPTION_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(Response.Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
        assertEquals("VOTE_DUPLICATE", ex.errorCode());
        assertEquals("votes", ex.domain());
    }

    @Test
    void castVote_differentPost_sameUser_accepted() {
        // A user can vote on different posts — uniqueness is per (post_id, user_id), not per user.
        long anotherPost = POST_ID + 1;
        long anotherOption = anotherPost * 10 + 1;
        when(voteRepository.existsByPostAndUser(anotherPost, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());

        VoteResponseDto result = voteService.castVote(anotherPost, anotherOption, VOTER_EMAIL, AUTH);

        assertEquals(anotherPost, result.postId());
        assertEquals(anotherOption, result.optionId());
        Vote persisted = capturePersistedVote();
        assertEquals(anotherPost, persisted.getPostId());
        assertEquals(USER_ID, persisted.getUserId());
        assertEquals(anotherOption, persisted.getOptionId());
        assertEquals(CharacteristicSnapshot.UNKNOWN, persisted.getSnapshot().bucketFor("ageRange"));
    }

    @Test
    void castVote_unknownEmail_throws401() {
        // user-service returns 204 (no user) — the service must reject with 401, not 500.
        // 204 = no user found; resolveUserId must throw 401 before touching the repo.
        when(userClient.getUserByEmail(VOTER_EMAIL, AUTH))
                .thenReturn(Response.noContent().build());

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.castVote(POST_ID, OPTION_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(401, ex.getResponse().getStatus());
        assertEquals("VOTE_USER_MISSING", ex.errorCode());
        assertEquals("votes", ex.domain());
        assertEquals("Authentication is required.", ex.publicMessage());
        verify(voteRepository, never()).existsByPostAndUser(anyLong(), anyLong());
    }

    @Test
    void getMyVote_differentUser_queriesWithOwnUserId() {
        // PII/isolation contract: getMyVote must derive the userId from the caller's email
        // (via user-service), never from shared state or a request parameter. If the userId
        // were leaked from another user's context, the repo would be called with USER_ID —
        // the verify(never()) below would catch that.
        long OTHER_ID = USER_ID + 1;
        String otherEmail = "other@test.com";
        String otherAuth = "Bearer other-token";

        Response otherRef = Response.ok(new UserCharacteristicClient.UserRef(OTHER_ID)).build();
        when(userClient.getUserByEmail(otherEmail, otherAuth)).thenReturn(otherRef);
        when(voteRepository.findByPostAndUser(POST_ID, OTHER_ID)).thenReturn(Optional.empty());

        Optional<VoteResponseDto> result = voteService.getMyVote(POST_ID, otherEmail, otherAuth);

        assertTrue(result.isEmpty());
        verify(voteRepository).findByPostAndUser(POST_ID, OTHER_ID);
        verify(voteRepository, never()).findByPostAndUser(POST_ID, USER_ID);
    }

    @Test
    void getMyVote_existingVote_mapsResponseWithoutPii() throws Exception {
        Vote vote = new Vote(POST_ID, USER_ID, OPTION_ID, CharacteristicSnapshot.empty());
        setId(vote, 44L);
        when(voteRepository.findByPostAndUser(POST_ID, USER_ID)).thenReturn(Optional.of(vote));

        Optional<VoteResponseDto> result = voteService.getMyVote(POST_ID, VOTER_EMAIL, AUTH);

        assertTrue(result.isPresent());
        assertEquals(44L, result.get().id());
        assertEquals(POST_ID, result.get().postId());
        assertEquals(OPTION_ID, result.get().optionId());
        verify(voteRepository).findByPostAndUser(POST_ID, USER_ID);
    }

    // ── assertVotableSelection ────────────────────────────────────────────────

    @Test
    void assertVotableSelection_nullPostId_throws400() {
        // A missing postId is a bad request — rejected before any lookup or write.
        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.assertVotableSelection(null, OPTION_ID)
        );

        assertEquals(400, ex.getResponse().getStatus());
        assertEquals("VOTE_INVALID", ex.errorCode());
        assertEquals("votes", ex.domain());
    }

    @Test
    void assertVotableSelection_optionFromThePost_passes() {
        voteService.assertVotableSelection(POST_ID, OPTION_ID);
    }

    @Test
    void assertVotableSelection_rejectsMissingAndCrossPostOptions() {
        VoteApiException missing = assertThrows(VoteApiException.class,
                () -> voteService.assertVotableSelection(POST_ID, null));
        assertEquals("VOTE_INVALID", missing.errorCode());

        VoteApiException foreign = assertThrows(VoteApiException.class,
                () -> voteService.assertVotableSelection(POST_ID, OPTION_ID + 1));
        assertEquals(400, foreign.getResponse().getStatus());
        assertEquals("VOTE_OPTION_NOT_AVAILABLE", foreign.errorCode());
    }

    @Test
    void assertVotableSelection_rejectsUnknownPost() {
        when(votingConfigurationService.findByPostId(POST_ID)).thenReturn(Optional.empty());
        VoteApiException error = assertThrows(VoteApiException.class,
                () -> voteService.assertVotableSelection(POST_ID, OPTION_ID));
        assertEquals(404, error.getResponse().getStatus());
        assertEquals("VOTE_POST_MISSING", error.errorCode());
    }

    @Test
    void castVote_unknownPostOnPersist_throws404() {
        // The fk_votes_post foreign key rejects a vote on a non-existent post; the service maps
        // that violation to a 404 rather than leaking a 500.
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());
        doThrow(new RuntimeException(
                "insert or update on table \"votes\" violates foreign key constraint \"fk_votes_post\""))
                .when(voteRepository).flush();

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.castVote(POST_ID, OPTION_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(404, ex.getResponse().getStatus());
        assertEquals("VOTE_POST_MISSING", ex.errorCode());
        assertEquals("votes", ex.domain());
    }

    // ── assertResultsUnlocked (Stage 4 results gating) ────────────────────────

    @Test
    void assertResultsUnlocked_unknownPost_throws404() {
        // Post existence is checked first — an unknown post is 404 before anything vote-related.
        when(voteRepository.postExists(POST_ID)).thenReturn(false);

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.assertResultsUnlocked(POST_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(404, ex.getResponse().getStatus());
        assertEquals("VOTE_POST_MISSING", ex.errorCode());
        // Never resolves the user or reads votes when the post itself does not exist.
        verify(userClient, never()).getUserByEmail(anyString(), anyString());
        verify(voteRepository, never()).findByPostAndUser(anyLong(), anyLong());
    }

    @Test
    void assertResultsUnlocked_callerHasNotVoted_throws403() {
        when(voteRepository.postExists(POST_ID)).thenReturn(true);
        when(voteRepository.findByPostAndUser(POST_ID, USER_ID)).thenReturn(Optional.empty());

        VoteApiException ex = assertThrows(
                VoteApiException.class,
                () -> voteService.assertResultsUnlocked(POST_ID, VOTER_EMAIL, AUTH)
        );

        assertEquals(403, ex.getResponse().getStatus());
        assertEquals("VOTE_RESULTS_LOCKED", ex.errorCode());
        assertEquals("votes", ex.domain());
    }

    @Test
    void assertResultsUnlocked_callerHasVoted_passes() {
        when(voteRepository.postExists(POST_ID)).thenReturn(true);
        when(voteRepository.findByPostAndUser(POST_ID, USER_ID))
                .thenReturn(Optional.of(new Vote(POST_ID, USER_ID, OPTION_ID, CharacteristicSnapshot.empty())));

        // No throw = results unlocked for a caller who has voted on the post.
        voteService.assertResultsUnlocked(POST_ID, VOTER_EMAIL, AUTH);
    }

    private Vote capturePersistedVote() {
        ArgumentCaptor<Vote> captor = ArgumentCaptor.forClass(Vote.class);
        verify(voteRepository).persist(captor.capture());
        return captor.getValue();
    }

    private static void setId(Vote vote, Long id) throws Exception {
        Field field = Vote.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(vote, id);
    }

    private static PostVotingConfigurationDto configuration(long postId) {
        return new PostVotingConfigurationDto(postId, VotingType.MULTIPLE_CHOICE,
                java.util.List.of(new VoteOptionDto(postId * 10 + 1, "More frequent buses", 0, null)));
    }
}
