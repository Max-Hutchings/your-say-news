package com.yoursay.autopost.model;

import com.yoursay.autopost.AutoPostRunStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AutoPostRunRepository implements PanacheRepositoryBase<AutoPostRun, UUID> {

    public Optional<AutoPostRun> claimable(Instant now) {
        return find("status = ?1 and (nextAttemptAt is null or nextAttemptAt <= ?2) order by createdAt",
                AutoPostRunStatus.QUEUED, now)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }

    public Optional<AutoPostRun> findForUpdate(UUID id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public List<AutoPostRun> listRecent() {
        return find("order by createdAt desc").page(0, 50).list();
    }
}
