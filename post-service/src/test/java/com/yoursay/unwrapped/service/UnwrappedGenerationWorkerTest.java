package com.yoursay.unwrapped.service;

import com.yoursay.platform.ai.AiConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnwrappedGenerationWorkerTest {

    @Test
    void disabledUnwrappedNeverClaimsAJobEvenWithAConfiguredProvider() {
        UnwrappedGenerationWorker worker = workerWith(false);

        worker.processNext();

        verifyNoInteractions(worker.processor);
    }

    @Test
    void enabledUnwrappedClaimsTheNextJob() {
        UnwrappedGenerationWorker worker = workerWith(true);
        when(worker.processor.claimNext()).thenReturn(Optional.empty());

        worker.processNext();

        verify(worker.processor).claimNext();
    }

    private static UnwrappedGenerationWorker workerWith(boolean enabled) {
        UnwrappedGenerationWorker worker = new UnwrappedGenerationWorker();
        worker.processor = Mockito.mock(UnwrappedJobProcessor.class);
        worker.aiConfig = Mockito.mock(AiConfig.class, Mockito.RETURNS_DEEP_STUBS);
        when(worker.aiConfig.unwrapped().configured()).thenReturn(true);
        worker.featureFlags = new UnwrappedFeatureFlags();
        worker.featureFlags.enabled = enabled;
        return worker;
    }
}
