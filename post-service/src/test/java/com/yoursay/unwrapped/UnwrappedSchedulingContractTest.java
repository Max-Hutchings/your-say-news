package com.yoursay.unwrapped;

import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import io.quarkus.scheduler.Scheduled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnwrappedSchedulingContractTest {
    @Test
    void reconciliationSchedulesOnlyConsumeExplicitWorkAndRecoverStaleClaims() {
        Set<String> scheduledIdentities = Arrays.stream(
                        UnwrappedReconciliationWorker.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Scheduled.class))
                .filter(annotation -> annotation != null)
                .map(Scheduled::identity)
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "unwrapped-milestone-reconciliation",
                "unwrapped-stale-claim-recovery"), scheduledIdentities);
    }
}
