package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.UnwrappedMilestoneService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UnwrappedMilestoneServiceImpl implements UnwrappedMilestoneService {
    @Inject
    EntityManager entityManager;

    /**
     * Marks this post dirty in the same Postgres database and transaction as the canonical vote.
     *
     * <p>This deliberately uses a native {@code INSERT ... ON CONFLICT} rather than a Panache
     * find-then-persist/update sequence. Concurrent votes can mark the same post at once, so the
     * marker must be an atomic, idempotent upsert; modelling the marker as a Panache entity would
     * still require native SQL to avoid a duplicate-key race.</p>
     */
    @Override
    @Transactional
    public void markForReconciliation(Long postId) {
        entityManager.createNativeQuery("""
                insert into unwrapped_reconciliation(post_id, dirty_at)
                values (?1, now())
                on conflict (post_id) do update set dirty_at = excluded.dirty_at
                """)
                .setParameter(1, postId)
                .executeUpdate();
    }
}
