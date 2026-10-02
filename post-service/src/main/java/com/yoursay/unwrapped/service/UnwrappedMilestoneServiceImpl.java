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
     * Marks a post dirty for asynchronous reconciliation, when an administrator triggers generation
     * or a voter taps Unwrap on a post with enough votes.
     *
     * <p>This deliberately uses a native {@code INSERT ... ON CONFLICT} rather than a Panache
     * find-then-persist/update sequence. Repeated admin or voter requests can target the same post,
     * so the marker remains an atomic, idempotent upsert; modelling it as a Panache entity would
     * still require native SQL to avoid a duplicate-key race. Vote casting never calls this
     * service.</p>
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
