package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.UnwrappedAvailabilityState;
import com.yoursay.unwrapped.dto.UnwrappedResponseDto;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJobRepository;
import com.yoursay.unwrapped.model.UnwrappedFollowUpRepository;
import com.yoursay.unwrapped.model.UnwrappedStoryRepository;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.UserAccessDto;
import com.yoursay.votes.VoteService;
import com.yoursay.votes.dto.VoteResponseDto;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnwrappedServiceImplTest {
    @Test
    void returnsInsufficientEvidenceWhenGenerationFoundNoReliableCohort() {
        VoteService votes = mock(VoteService.class);
        when(votes.getMyVote(42L, "voter@example.com", "Bearer token"))
                .thenReturn(Optional.of(new VoteResponseDto(8L, 42L, 101L)));
        when(votes.countForPost(42L)).thenReturn(640L);
        UnwrappedStoryRepository stories = mock(UnwrappedStoryRepository.class);
        when(stories.newestApproved(42L, 640L)).thenReturn(Optional.empty());
        UnwrappedFollowUpRepository followUps = mock(UnwrappedFollowUpRepository.class);
        when(followUps.findByUserAndPost(17L, 42L)).thenReturn(Optional.empty());
        YourSayUserService users = mock(YourSayUserService.class);
        when(users.getAccessByEmail("voter@example.com"))
                .thenReturn(new UserAccessDto(17L, null, null, false));
        UnwrappedAnalysisJobRepository jobs = mock(UnwrappedAnalysisJobRepository.class);
        when(jobs.count("postId = ?1 and status in ('PENDING','GENERATING','DRAFT_READY')", 42L))
                .thenReturn(0L);
        when(jobs.count("postId = ?1 and status = 'FAILED' and errorCode = ?2", 42L,
                "UNWRAPPED_INSUFFICIENT_DEMOGRAPHIC_EVIDENCE")).thenReturn(1L);
        UnwrappedServiceImpl service = new UnwrappedServiceImpl();
        service.voteService = votes;
        service.storyRepository = stories;
        service.followUpRepository = followUps;
        service.userService = users;
        service.jobRepository = jobs;

        UnwrappedResponseDto response = service.get(
                42L, "voter@example.com", "Bearer token");

        assertEquals(UnwrappedAvailabilityState.INSUFFICIENT_EVIDENCE, response.state());
        assertEquals("There is no statistically reliable demographic pattern for every option yet.",
                response.notice());
        assertEquals(101L, response.originalOptionId());
        assertNull(response.existingFollowUpOptionId());
        assertNull(response.story());
    }
}
