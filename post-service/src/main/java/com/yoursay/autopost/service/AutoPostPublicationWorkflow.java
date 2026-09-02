package com.yoursay.autopost.service;

import com.yoursay.autopost.AutoPostRunStatus;
import com.yoursay.autopost.error.AutoPostApiException;
import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.autopost.model.AutoPostCandidateRepository;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.autopost.model.AutoPostRunRepository;
import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.posts.PostService;
import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.PepperDraftStatus;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AutoPostAgentDraftDto;
import com.yoursay.user.user.dto.UserAccessDto;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.vertx.VertxContextSupport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.function.Supplier;

/** Publishes one approved draft while preserving a recoverable run state on failure. */
@ApplicationScoped
public class AutoPostPublicationWorkflow {

    @Inject
    AutoPostRunRepository runs;

    @Inject
    AutoPostCandidateRepository candidates;

    @Inject
    AutoPostAgentService postAgentService;

    @Inject
    PostService postService;

    @Inject
    AutoPostAccessPolicy accessPolicy;

    @Inject
    AutoPostDraftWorkflow draftWorkflow;

    @Inject
    DomainMetrics metrics;

    public AutoPostRun approveAndPublishDraft(UUID runId) {
        AutoPostRun run = draftWorkflow.synchronizeDraft(requireRun(runId));
        if (run.getStatus() == AutoPostRunStatus.PUBLISHED) {
            return run;
        }

        UserAccessDto official = accessPolicy.requireOfficialAccount();
        ApprovalWork work = reservePublication(runId, official.userId());
        return publishApprovedDraft(work);
    }

    private AutoPostRun requireRun(UUID runId) {
        return runs.findByIdOptional(runId).orElseThrow(AutoPostApiException::runMissing);
    }

    private AutoPostRun requireRunForUpdate(UUID runId) {
        return runs.findForUpdate(runId).orElseThrow(AutoPostApiException::runMissing);
    }

    private ApprovalWork reservePublication(UUID runId, long officialUserId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            AutoPostRun run = requireRunForUpdate(runId);
            requireDraftReadyForPublication(run);
            run.markPublishing();
            runs.flush();
            return new ApprovalWork(
                    run.getId(),
                    run.getSelectedCandidateId(),
                    run.getPepperDraftId(),
                    officialUserId);
        });
    }

    private static void requireDraftReadyForPublication(AutoPostRun run) {
        if (run.getStatus() != AutoPostRunStatus.DRAFT_READY
                || run.getPepperDraftId() == null
                || run.getSelectedCandidateId() == null) {
            throw AutoPostApiException.approvalConflict();
        }
    }

    private AutoPostRun publishApprovedDraft(ApprovalWork work) {
        long publicationStarted = System.nanoTime();
        AutoPostLog.started("publication", "publication_workflow");
        try {
            AutoPostAgentDraftDto draft = loadCompletedDraft(work);
            AgentPublicationDto provenance = preparePublication(work, draft);
            AutoPostCandidate candidate = loadPublicationCandidate(work);
            PostDto post = createPost(work, candidate, draft, provenance);
            markPostAgentDraftPublished(work, post.id());
            AutoPostRun published = markRunPublished(work, post.id());

            recordOperation("publication", "success", "none", "none", publicationStarted);
            AutoPostLog.succeeded("publication", "publication_workflow");
            return published;
        } catch (AutoPostApiException error) {
            restoreDraftReadyAfterFailure(work.runId(), error);
            recordOperation("publication", "error", "workflow", error.errorCode(),
                    publicationStarted);
            AutoPostLog.rejected("publication", "publication_workflow", error.errorCode());
            throw error;
        } catch (RuntimeException error) {
            restoreDraftReadyAfterFailure(work.runId(), error);
            recordOperation("publication", "fault", "application",
                    "AUTO_POST_PUBLICATION_FAILED", publicationStarted);
            AutoPostLog.failed("publication", "publication_workflow", "application",
                    "AUTO_POST_PUBLICATION_FAILED", error);
            throw AutoPostApiException.publicationFailed();
        }
    }

    private AutoPostAgentDraftDto loadCompletedDraft(ApprovalWork work) {
        return executePublicationStage(PublicationStage.DRAFT_READ, () -> {
            AutoPostAgentDraftDto draft = postAgentService
                    .getForPublisher(work.draftId(), work.publisherUserId())
                    .orElseThrow(AutoPostApiException::approvalConflict);
            requireCompletedDraftContent(draft);
            return draft;
        });
    }

    private static void requireCompletedDraftContent(AutoPostAgentDraftDto draft) {
        if (draft.status() != PepperDraftStatus.FINISHED
                || !Boolean.TRUE.equals(draft.success())
                || draft.content() == null) {
            throw AutoPostApiException.approvalConflict();
        }
    }

    private AgentPublicationDto preparePublication(
            ApprovalWork work,
            AutoPostAgentDraftDto draft
    ) {
        return executePublicationStage(PublicationStage.PUBLICATION_PREPARATION,
                () -> postAgentService.preparePublicationForPublisher(
                        work.draftId(),
                        work.publisherUserId(),
                        draft.content().citations()));
    }

    private AutoPostCandidate loadPublicationCandidate(ApprovalWork work) {
        return executePublicationStage(PublicationStage.CANDIDATE_READ,
                () -> candidates.findInRun(work.candidateId(), work.runId())
                        .orElseThrow(AutoPostApiException::candidateMissing));
    }

    private PostDto createPost(
            ApprovalWork work,
            AutoPostCandidate candidate,
            AutoPostAgentDraftDto draft,
            AgentPublicationDto provenance
    ) {
        return executePublicationStage(PublicationStage.POST_PERSISTENCE,
                () -> createPostOnManagedContext(work, candidate, draft, provenance));
    }

    private PostDto createPostOnManagedContext(
            ApprovalWork work,
            AutoPostCandidate candidate,
            AutoPostAgentDraftDto draft,
            AgentPublicationDto provenance
    ) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> postService.createForPublisher(
                    work.publisherUserId(),
                    AutoPostPublicationRequestFactory.createRequest(
                            candidate, draft.content(), work.draftId()),
                    AutoPostPublicationRequestFactory.createProvenance(provenance)));
        } catch (RuntimeException error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException("Post persistence failed", error);
        }
    }

    private void markPostAgentDraftPublished(ApprovalWork work, long postId) {
        executePublicationStage(PublicationStage.DRAFT_FINALIZATION, () -> {
            postAgentService.markPublished(work.draftId(), postId);
            return null;
        });
    }

    private AutoPostRun markRunPublished(ApprovalWork work, long postId) {
        return executePublicationStage(PublicationStage.RUN_FINALIZATION,
                () -> QuarkusTransaction.requiringNew().call(() -> {
                    AutoPostRun run = requireRunForUpdate(work.runId());
                    run.markPublished(postId);
                    runs.flush();
                    return run;
                }));
    }

    private <T> T executePublicationStage(PublicationStage stage, Supplier<T> work) {
        long started = System.nanoTime();
        AutoPostLog.started(stage.operation, stage.stage);
        try {
            T result = work.get();
            recordOperation(stage.operation, "success", "none", "none", started);
            AutoPostLog.succeeded(stage.operation, stage.stage);
            return result;
        } catch (AutoPostApiException error) {
            recordOperation(stage.operation, "error", "workflow", error.errorCode(), started);
            AutoPostLog.rejected(stage.operation, stage.stage, error.errorCode());
            throw error;
        } catch (RuntimeException error) {
            recordOperation(stage.operation, "fault", stage.faultType, stage.faultCode, started);
            AutoPostLog.failed(stage.operation, stage.stage, stage.faultType, stage.faultCode, error);
            throw error;
        }
    }

    private void restoreDraftReadyAfterFailure(UUID runId, RuntimeException originalFailure) {
        try {
            restoreDraftReady(runId);
        } catch (RuntimeException recoveryFailure) {
            originalFailure.addSuppressed(recoveryFailure);
        }
    }

    private void restoreDraftReady(UUID runId) {
        long started = System.nanoTime();
        AutoPostLog.started("publicationRecovery", "publication_state_recovery");
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                AutoPostRun run = requireRunForUpdate(runId);
                if (run.getStatus() == AutoPostRunStatus.PUBLISHING) {
                    run.markPublicationReadyForRetry(
                            "AUTO_POST_PUBLICATION_FAILED",
                            "The approved post could not be published. Try again.");
                    runs.flush();
                }
            });
            recordOperation("publicationRecovery", "success", "none", "none", started);
            AutoPostLog.succeeded("publicationRecovery", "publication_state_recovery");
        } catch (RuntimeException error) {
            recordOperation("publicationRecovery", "fault", "database",
                    "AUTO_POST_PUBLICATION_RECOVERY_FAILED", started);
            AutoPostLog.failed("publicationRecovery", "publication_state_recovery", "database",
                    "AUTO_POST_PUBLICATION_RECOVERY_FAILED", error);
            throw error;
        }
    }

    private void recordOperation(
            String operation,
            String outcome,
            String errorType,
            String errorCode,
            long started
    ) {
        metrics.recordOperation("autopost", operation, outcome, errorType, errorCode,
                System.nanoTime() - started);
    }

    private enum PublicationStage {
        DRAFT_READ(
                "postAgentDraftRead",
                "post_agent_draft_read",
                "dependency",
                "AUTO_POST_DRAFT_READ_FAILED"),
        PUBLICATION_PREPARATION(
                "postAgentPublicationPreparation",
                "post_agent_publication_preparation",
                "dependency",
                "AUTO_POST_PUBLICATION_PREPARATION_FAILED"),
        CANDIDATE_READ(
                "publicationCandidateRead",
                "candidate_read",
                "database",
                "AUTO_POST_CANDIDATE_READ_FAILED"),
        POST_PERSISTENCE(
                "postPersistence",
                "post_persistence",
                "downstream_domain",
                "AUTO_POST_POST_PERSISTENCE_FAILED"),
        DRAFT_FINALIZATION(
                "postAgentDraftFinalization",
                "post_agent_draft_finalization",
                "dependency",
                "AUTO_POST_DRAFT_FINALIZATION_FAILED"),
        RUN_FINALIZATION(
                "publicationStatePersistence",
                "publication_state_persistence",
                "database",
                "AUTO_POST_PUBLICATION_STATE_FAILED");

        private final String operation;
        private final String stage;
        private final String faultType;
        private final String faultCode;

        PublicationStage(String operation, String stage, String faultType, String faultCode) {
            this.operation = operation;
            this.stage = stage;
            this.faultType = faultType;
            this.faultCode = faultCode;
        }
    }

    private record ApprovalWork(
            UUID runId,
            UUID candidateId,
            UUID draftId,
            long publisherUserId
    ) {
    }
}
