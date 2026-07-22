package com.yoursay.agents.postagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agents.postagent.AgentDraftDto;
import com.yoursay.agents.postagent.AgentJobStatus;
import com.yoursay.agents.postagent.AgentSourceDto;
import com.yoursay.agents.postagent.SourcedClaimDto;
import com.yoursay.agents.postagent.generator.GenerationException;
import com.yoursay.agents.postagent.generator.GenerationResult;
import com.yoursay.agents.postagent.model.AgentGenerationJob;
import com.yoursay.agents.postagent.model.AgentGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentJobProcessorTest {

    private AgentGenerationJobRepository repository;
    private AgentJobProcessor processor;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(AgentGenerationJobRepository.class);
        processor = new AgentJobProcessor();
        processor.repository = repository;
        processor.objectMapper = new ObjectMapper();
        processor.maxAttempts = 3;
    }

    @Test
    void retryableFirstFailureReturnsJobToPendingWithBoundedBackoff() {
        AgentGenerationJob job = researchingJob(1);
        Mockito.when(repository.findByIdOptional(job.getId())).thenReturn(Optional.of(job));
        Instant before = Instant.now();

        processor.fail(job.getId(), 1,
                new GenerationException("AGENT_PROVIDER_HTTP_503", "provider detail", true));

        assertEquals(AgentJobStatus.PENDING, job.getStatus());
        assertEquals("AGENT_PROVIDER_HTTP_503", job.getErrorCode());
        assertEquals("Generation will be retried.", job.getErrorMessage());
        assertNotNull(job.getNextAttemptAt());
        assertTrue(job.getNextAttemptAt().isAfter(before.plusSeconds(50)));
        assertTrue(job.getNextAttemptAt().isBefore(before.plusSeconds(70)));
    }

    @Test
    void thirdRetryableFailureStopsRetryingAndExposesOnlySafeMessage() {
        AgentGenerationJob job = researchingJob(3);
        Mockito.when(repository.findByIdOptional(job.getId())).thenReturn(Optional.of(job));

        processor.fail(job.getId(), 3,
                new GenerationException("AGENT_PROVIDER_HTTP_503",
                        "upstream response contained private account information", true));

        assertEquals(AgentJobStatus.FAILED, job.getStatus());
        assertEquals("AGENT_PROVIDER_HTTP_503", job.getErrorCode());
        assertEquals("Post generation failed. Please try again.", job.getErrorMessage());
        assertNotNull(job.getCompletedAt());
        assertNull(job.getNextAttemptAt());
    }

    @Test
    void nonRetryableFirstFailureFailsImmediatelyWithValidationMessage() {
        AgentGenerationJob job = researchingJob(1);
        Mockito.when(repository.findByIdOptional(job.getId())).thenReturn(Optional.of(job));

        processor.fail(job.getId(), 1,
                new GenerationException("AGENT_INVALID_PROVIDER_OUTPUT",
                        "claim cited a fabricated URL", false));

        assertEquals(AgentJobStatus.FAILED, job.getStatus());
        assertEquals("AGENT_INVALID_PROVIDER_OUTPUT", job.getErrorCode());
        assertEquals("The generated draft did not pass source validation.", job.getErrorMessage());
        assertNull(job.getNextAttemptAt());
    }

    @Test
    void completingJobStoresExactDraftAndProviderModel() throws Exception {
        AgentGenerationJob job = researchingJob(1);
        Mockito.when(repository.findByIdOptional(job.getId())).thenReturn(Optional.of(job));
        AgentDraftDto draft = draft();

        processor.complete(job.getId(), new GenerationResult(draft, "grok-4.3", "resp_policy_123"));

        assertEquals(AgentJobStatus.DRAFT_READY, job.getStatus());
        assertEquals("grok-4.3", job.getModel());
        assertEquals("Should the policy be adopted?",
                processor.objectMapper.treeToValue(job.getDraft(), AgentDraftDto.class).supportQuestion());
        assertEquals("A verified factual statement.",
                processor.objectMapper.treeToValue(job.getDraft(), AgentDraftDto.class)
                        .summaryClaims().getFirst().text());
        assertNotNull(job.getCompletedAt());
    }

    private static AgentGenerationJob researchingJob(int attempts) {
        AgentGenerationJob job = new AgentGenerationJob(81L,
                "Compare the evidence and strongest arguments around a current policy.");
        for (int i = 0; i < attempts; i++) {
            job.markResearching();
        }
        return job;
    }

    private static AgentDraftDto draft() {
        String sourceA = "https://www.gov.uk/policy";
        String sourceB = "https://ifs.org.uk/policy";
        return new AgentDraftDto(
                List.of(new SourcedClaimDto("A verified factual statement.", List.of(sourceA))),
                List.of(new SourcedClaimDto("The strongest supporting argument.", List.of(sourceA))),
                List.of(new SourcedClaimDto("The strongest opposing argument.", List.of(sourceB))),
                "Should the policy be adopted?",
                List.of(
                        new AgentSourceDto(sourceA, "Policy", "UK Government"),
                        new AgentSourceDto(sourceB, "Analysis", "Institute for Fiscal Studies")),
                "A neutral image of the institution responsible for the policy.",
                "responsible institution reusable licensed image");
    }
}
