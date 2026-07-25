package com.yoursay.unwrapped.service;

import com.yoursay.unwrapped.UnwrappedMode;
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

    @Scheduled(identity = "unwrapped-milestone-reconciliation",
            every = "${unwrapped.jobs.reconcile-interval:2s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    void reconcileOne() {
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
        for (Integer milestone : MILESTONES) {
            if (count >= milestone && jobs.count(
                    "postId = ?1 and mode = ?2 and milestone = ?3 and analysisVersion = ?4",
                    postId, UnwrappedMode.OBSERVED, milestone, "unwrapped-analysis-v1") == 0) {
                jobs.persist(new UnwrappedAnalysisJob(UnwrappedMode.OBSERVED, postId, milestone,
                        "unwrapped-analysis-v1", null));
            }
        }
        entityManager.createNativeQuery("delete from unwrapped_reconciliation where post_id = ?1")
                .setParameter(1, postId).executeUpdate();
    }

    /** Recovery path that ensures every published post eventually gets one prediction draft. */
    @Scheduled(identity = "unwrapped-prediction-reconciliation",
            every = "${unwrapped.jobs.prediction-scan-interval:30s}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    void enqueueMissingPrediction() {
        @SuppressWarnings("unchecked")
        List<Number> postIds = entityManager.createNativeQuery("""
                select p.id
                from post p
                where not exists (
                    select 1 from unwrapped_analysis_job j
                    where j.post_id = p.id and j.mode = 'PREDICTION'
                      and j.prediction_version = 'prediction-v1'
                )
                order by p.created_at
                limit 1
                """).getResultList();
        if (postIds.isEmpty()) return;
        jobs.persist(new UnwrappedAnalysisJob(UnwrappedMode.PREDICTION,
                postIds.getFirst().longValue(), null, "unwrapped-analysis-v1", "prediction-v1"));
    }

    @Scheduled(identity = "unwrapped-stale-claim-recovery",
            every = "${unwrapped.jobs.claim-recovery-interval:1m}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    void recoverStaleClaims() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        jobs.staleClaims(cutoff).forEach(UnwrappedAnalysisJob::recoverStaleClaim);
    }

    /** Defence-in-depth sweep for a vote transaction whose dirty marker was ever missed. */
    @Scheduled(identity = "unwrapped-milestone-backfill",
            every = "${unwrapped.jobs.milestone-scan-interval:5m}", concurrentExecution = SKIP)
    @RunOnVirtualThread
    @Transactional
    void enqueueMissedMilestones() {
        entityManager.createNativeQuery("""
                with vote_counts as (
                    select post_id, count(*) as vote_count
                    from votes
                    group by post_id
                ),
                crossed as (
                    select distinct counts.post_id
                    from vote_counts counts
                    cross join (values (100), (250), (500), (1000)) as configured(milestone)
                    where counts.vote_count >= configured.milestone
                      and not exists (
                          select 1
                          from unwrapped_analysis_job job
                          where job.post_id = counts.post_id
                            and job.mode = 'OBSERVED'
                            and job.milestone = configured.milestone
                            and job.analysis_version = 'unwrapped-analysis-v1'
                      )
                )
                insert into unwrapped_reconciliation(post_id, dirty_at)
                select post_id, now() from crossed
                on conflict (post_id) do update set dirty_at = excluded.dirty_at
                """).executeUpdate();
    }
}
