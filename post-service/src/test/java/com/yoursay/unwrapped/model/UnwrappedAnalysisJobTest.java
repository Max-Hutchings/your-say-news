package com.yoursay.unwrapped.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnwrappedAnalysisJobTest {
    @Test
    void staleClaimsReturnToPendingUntilTheRetryBudgetIsExhausted() {
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(42L, 100, "analysis-v1");

        job.claim();
        job.recoverStaleClaim(true);
        assertEquals(UnwrappedJobStatus.PENDING, job.getStatus());
        assertEquals(1, job.getAttemptCount());

        job.claim();
        job.recoverStaleClaim(true);
        assertEquals(UnwrappedJobStatus.PENDING, job.getStatus());

        job.claim();
        job.recoverStaleClaim(true);
        assertEquals(UnwrappedJobStatus.FAILED, job.getStatus());
        assertEquals(3, job.getAttemptCount());
    }

    @Test
    void staleClaimFailsImmediatelyWhenRetriesAreDisabled() {
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(42L, 500, "analysis-v1");

        job.claim();
        job.recoverStaleClaim(false);

        assertEquals(UnwrappedJobStatus.FAILED, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals("A generation worker stopped before completing this job.",
                job.getErrorMessage());
    }

    @Test
    void generationFailureFailsImmediatelyWhenRetriesAreDisabled() {
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(42L, 500, "analysis-v1");

        job.claim();
        job.fail("UNWRAPPED_PAGE_UNSOURCED", "No sourced claims were returned.", false);

        assertEquals(UnwrappedJobStatus.FAILED, job.getStatus());
        assertEquals(1, job.getAttemptCount());
        assertEquals("No sourced claims were returned.", job.getErrorMessage());
    }
}
