package com.yoursay.votes;

import com.yoursay.votes.client.UserCharacteristicClient;
import com.yoursay.votes.model.VoteRepository;
import com.yoursay.votes.service.VoteServiceImpl;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    VoteServiceImpl voteService;

    private static final String VOTER_EMAIL = "voter@test.com";
    private static final String AUTH = "Bearer test-token";
    private static final long USER_ID = 10L;
    private static final long POST_ID = 5L;

    @BeforeEach
    void stubUserLookup() {
        // lenient: castVote_unknownEmail_throws401 and getMyVote_differentUser_queriesWithOwnUserId
        // override or don't use this stub, so strict mode would flag it without lenient().
        Response userRefResp = Response.ok(new UserCharacteristicClient.UserRef(USER_ID)).build();
        lenient().when(userClient.getUserByEmail(VOTER_EMAIL, AUTH)).thenReturn(userRefResp);
    }

    @Test
    void castVote_firstVote_accepted() {
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());
        // persist() is void — default mock does nothing, which is correct.

        VoteResponseDto result = voteService.castVote(POST_ID, true, VOTER_EMAIL, AUTH);

        assertEquals(POST_ID, result.postId());
        assertEquals(true, result.voteFor());
        verify(voteRepository).persist(any(com.yoursay.votes.model.Vote.class));
    }

    @Test
    void castVote_secondVote_samePoster_throws409() {
        // The duplicate-vote guard fires before any persist call.
        when(voteRepository.existsByPostAndUser(POST_ID, USER_ID)).thenReturn(true);

        ClientErrorException ex = assertThrows(
                ClientErrorException.class,
                () -> voteService.castVote(POST_ID, true, VOTER_EMAIL, AUTH)
        );

        assertEquals(Response.Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
        verify(voteRepository, never()).persist(any(com.yoursay.votes.model.Vote.class));
    }

    @Test
    void castVote_differentPost_sameUser_accepted() {
        // A user can vote on different posts — uniqueness is per (post_id, user_id), not per user.
        long anotherPost = POST_ID + 1;
        when(voteRepository.existsByPostAndUser(anotherPost, USER_ID)).thenReturn(false);
        when(userClient.getMyCharacteristics(AUTH)).thenReturn(Response.noContent().build());

        VoteResponseDto result = voteService.castVote(anotherPost, false, VOTER_EMAIL, AUTH);

        assertEquals(anotherPost, result.postId());
        assertEquals(false, result.voteFor());
        verify(voteRepository).persist(any(com.yoursay.votes.model.Vote.class));
    }

    @Test
    void castVote_unknownEmail_throws401() {
        // user-service returns 204 (no user) — the service must reject with 401, not 500.
        // 204 = no user found; resolveUserId must throw 401 before touching the repo.
        when(userClient.getUserByEmail(VOTER_EMAIL, AUTH))
                .thenReturn(Response.noContent().build());

        WebApplicationException ex = assertThrows(
                WebApplicationException.class,
                () -> voteService.castVote(POST_ID, true, VOTER_EMAIL, AUTH)
        );

        assertEquals(401, ex.getResponse().getStatus());
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
}
