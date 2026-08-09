package com.yoursay.votes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.posts.VotingType;
import com.yoursay.votes.model.VoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostAnalysisAggregateServiceImplTest {
    @Test
    void stableDataHasAStableVersionAndCaptureUsesRepeatableRead() {
        PostVotingConfigurationDto post = new PostVotingConfigurationDto(
                42L, "Summary", "Question?", "GLOBAL", VotingType.BINARY,
                List.of(new VoteOptionDto(101L, "Agree", 0, "AGREE"),
                        new VoteOptionDto(102L, "Disagree", 1, "DISAGREE")));
        PostVotingConfigurationService posts = mock(PostVotingConfigurationService.class);
        when(posts.findByPostId(42L)).thenReturn(Optional.of(post));
        VoteRepository votes = mock(VoteRepository.class);
        when(votes.listByPost(42L)).thenReturn(List.of());
        EntityManager entityManager = mock(EntityManager.class);
        Query isolation = mock(Query.class);
        when(entityManager.createNativeQuery("set transaction isolation level repeatable read"))
                .thenReturn(isolation);

        PostAnalysisAggregateServiceImpl service = new PostAnalysisAggregateServiceImpl();
        service.postService = posts;
        service.voteRepository = votes;
        service.builder = new PostAnalysisAggregateBuilder();
        service.objectMapper = new ObjectMapper().findAndRegisterModules();
        service.entityManager = entityManager;
        service.suppressBelow = 0;

        var firstPayload = service.builder.build(post, List.of(), 0, Instant.EPOCH);
        var secondPayload = service.builder.build(post, List.of(), 0,
                Instant.EPOCH.plusSeconds(300));
        assertEquals(service.aggregateVersion(firstPayload),
                service.aggregateVersion(secondPayload));

        var captured = service.capture(42L);
        assertNotNull(captured.capturedAt());
        verify(entityManager)
                .createNativeQuery("set transaction isolation level repeatable read");
        verify(isolation).executeUpdate();
    }
}
