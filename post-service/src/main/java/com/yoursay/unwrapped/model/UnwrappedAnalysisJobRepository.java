package com.yoursay.unwrapped.model;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UnwrappedAnalysisJobRepository implements PanacheRepositoryBase<UnwrappedAnalysisJob, UUID> {
    public Optional<UnwrappedAnalysisJob> nextForUpdate() {
        return find("status = ?1 and (nextAttemptAt is null or nextAttemptAt <= ?2) order by createdAt",
                UnwrappedJobStatus.PENDING, Instant.now())
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public List<UnwrappedAnalysisJob> staleClaims(Instant cutoff) {
        return list("status = ?1 and startedAt <= ?2",
                UnwrappedJobStatus.GENERATING, cutoff);
    }
}
