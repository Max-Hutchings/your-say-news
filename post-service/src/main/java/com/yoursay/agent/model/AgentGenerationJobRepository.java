package com.yoursay.agent.model;

import com.yoursay.agent.AgentJobStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AgentGenerationJobRepository implements PanacheRepositoryBase<AgentGenerationJob, UUID> {

    public Optional<AgentGenerationJob> findOwned(UUID id, Long userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResultOptional();
    }

    public Optional<AgentGenerationJob> claimable(Instant now) {
        return find("status = ?1 and (nextAttemptAt is null or nextAttemptAt <= ?2) order by createdAt",
                AgentJobStatus.PENDING, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
