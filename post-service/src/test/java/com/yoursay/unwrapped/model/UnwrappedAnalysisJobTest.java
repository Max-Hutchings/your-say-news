package com.yoursay.unwrapped.model;

import com.yoursay.unwrapped.UnwrappedMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnwrappedAnalysisJobTest {
    @Test
    void staleClaimsReturnToPendingUntilTheRetryBudgetIsExhausted() {
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(UnwrappedMode.PREDICTION, 42L, null,
                        "analysis-v1", "prediction-v1");

        job.claim();
        job.recoverStaleClaim();
        assertEquals(UnwrappedJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());

        job.claim();
        job.recoverStaleClaim();
        assertEquals(UnwrappedJobStatus.PENDING, job.getStatus());

        job.claim();
        job.recoverStaleClaim();
        assertEquals(UnwrappedJobStatus.FAILED, job.getStatus());
        assertEquals(3, job.getAttemptCount());
    }
}
