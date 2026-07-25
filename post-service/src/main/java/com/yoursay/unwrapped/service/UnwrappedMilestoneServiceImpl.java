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
