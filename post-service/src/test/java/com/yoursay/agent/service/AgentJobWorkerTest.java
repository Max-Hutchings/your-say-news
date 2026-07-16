package com.yoursay.agent.service;

import com.yoursay.agent.generator.GenerationException;
import com.yoursay.agent.generator.GenerationResult;
import com.yoursay.agent.generator.UnbiasedPostGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

class AgentJobWorkerTest {

    private AgentJobProcessor processor;
    private UnbiasedPostGenerator generator;
    private AgentJobWorker worker;

    @BeforeEach
    void setUp() {
        processor = Mockito.mock(AgentJobProcessor.class);
        generator = Mockito.mock(UnbiasedPostGenerator.class);
        worker = new AgentJobWorker();
        worker.processor = processor;
        worker.generator = generator;
    }

    @Test
    void completedGenerationIsPersistedAgainstClaimedJob() {
        UUID id = UUID.randomUUID();
        AgentJobProcessor.JobWork work = new AgentJobProcessor.JobWork(
                id, "Cover a current policy dispute.", 1);
        GenerationResult result = new GenerationResult(null, "grok-4.3", "resp_policy_123");
        Mockito.when(processor.claimNext()).thenReturn(Optional.of(work));
        Mockito.when(generator.generate(work.request())).thenReturn(result);

        worker.processNext();

        Mockito.verify(generator).generate("Cover a current policy dispute.");
        Mockito.verify(processor).complete(id, result);
        Mockito.verify(processor, Mockito.never()).fail(
                Mockito.any(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    void generationFailurePreservesAttemptAndRetryClassification() {
        UUID id = UUID.randomUUID();
        AgentJobProcessor.JobWork work = new AgentJobProcessor.JobWork(
                id, "Cover a current policy dispute.", 2);
        GenerationException failure = new GenerationException(
                "AGENT_PROVIDER_HTTP_503", "temporary failure", true);
        Mockito.when(processor.claimNext()).thenReturn(Optional.of(work));
        Mockito.when(generator.generate(work.request())).thenThrow(failure);

        worker.processNext();

        Mockito.verify(processor).fail(id, 2, failure);
        Mockito.verify(processor, Mockito.never()).complete(
                Mockito.any(), Mockito.any());
    }

    @Test
    void idlePollDoesNotCallProvider() {
        Mockito.when(processor.claimNext()).thenReturn(Optional.empty());

        worker.processNext();

        Mockito.verifyNoInteractions(generator);
    }

    @Test
    void unexpectedRuntimeFailureIsWrappedAsRetryableWithoutRawMessage() {
        UUID id = UUID.randomUUID();
        AgentJobProcessor.JobWork work = new AgentJobProcessor.JobWork(
                id, "Cover a current policy dispute.", 1);
        Mockito.when(processor.claimNext()).thenReturn(Optional.of(work));
        Mockito.when(generator.generate(work.request()))
                .thenThrow(new IllegalStateException("sensitive internal detail"));

        worker.processNext();

        org.mockito.ArgumentCaptor<GenerationException> error =
                org.mockito.ArgumentCaptor.forClass(GenerationException.class);
        Mockito.verify(processor).fail(Mockito.eq(id), Mockito.eq(1), error.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "AGENT_UNEXPECTED_FAILURE", error.getValue().code());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Unexpected generation failure", error.getValue().getMessage());
        org.junit.jupiter.api.Assertions.assertTrue(error.getValue().retryable());
    }
}
