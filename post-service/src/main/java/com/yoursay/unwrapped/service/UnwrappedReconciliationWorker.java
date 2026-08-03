package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.model.UnwrappedAnalysisJob;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJobRepository;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class UnwrappedReconciliationWorker {
    private static final List<Integer> MILESTONES = List.of(100, 250, 500, 1000);

    @Inject
    EntityManager entityManager;
    @Inject
    UnwrappedAnalysisJobRepository jobs;

    /**
     * Claims and reconciles one dirty post as a single database transaction.
     *
     * <p>The marker claim uses PostgreSQL {@code FOR UPDATE SKIP LOCKED} so multiple service
     * instances cannot process the same post concurrently. The vote count is also read directly
     * because Unwrapped must not import the votes domain's internal entities or repositories.
     * Panache would not remove either database-specific operation, so native SQL keeps the queue
     * semantics explicit while Panache remains responsible for ordinary analysis-job persistence.</p>
     */
    @Scheduled(identity = "unwrapped-milestone-reconciliation",
            every = "${unwrapped.jobs.reconcile-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    public void reconcileOne() {
        @SuppressWarnings("unchecked")
        List<Number> postIds = entityManager.createNativeQuery("""
                select post_id from unwrapped_reconciliation
                order by dirty_at
                for update skip locked
                limit 1
                """).getResultList();
        if (postIds.isEmpty()) return;
        long postId = postIds.getFirst().longValue();
        long count = ((Number) entityManager.createNativeQuery(
                "select count(*) from votes where post_id = ?1")
                .setParameter(1, postId).getSingleResult()).longValue();
        Integer milestone = MILESTONES.reversed().stream()
                .filter(candidate -> count >= candidate)
                .findFirst()
                .orElse(null);
        if (milestone != null && jobs.count(
                "postId = ?1 and milestone = ?2 and analysisVersion = ?3",
                postId, milestone, "unwrapped-analysis-v1") == 0) {
            jobs.persist(new UnwrappedAnalysisJob(postId, milestone,
                    "unwrapped-analysis-v1"));
        }
        entityManager.createNativeQuery("delete from unwrapped_reconciliation where post_id = ?1")
                .setParameter(1, postId).executeUpdate();
    }

    @Scheduled(identity = "unwrapped-stale-claim-recovery",
            every = "${unwrapped.jobs.claim-recovery-interval:1m}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    void recoverStaleClaims() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        jobs.staleClaims(cutoff).forEach(UnwrappedAnalysisJob::recoverStaleClaim);
    }

}
