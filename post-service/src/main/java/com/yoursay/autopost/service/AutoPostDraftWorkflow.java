package com.yoursay.autopost.service;

import com.yoursay.autopost.AutoPostRunStatus;
import com.yoursay.autopost.error.AutoPostApiException;
import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.autopost.model.AutoPostCandidateRepository;
import com.yoursay.autopost.model.AutoPostCandidateSource;
import com.yoursay.autopost.model.AutoPostCandidateSourceRepository;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.autopost.model.AutoPostRunRepository;
import com.yoursay.autopost.observability.AutoPostLog;
import com.yoursay.platform.observability.DomainMetrics;
import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.PepperDraftStatus;
import com.yoursay.posts.postagent.dto.AutoPostAgentDraftDto;
import com.yoursay.user.user.dto.UserAccessDto;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Owns candidate selection and the post-agent draft state transition. */
@ApplicationScoped
public class AutoPostDraftWorkflow {

    @Inject
    AutoPostRunRepository runs;

    @Inject
    AutoPostCandidateRepository candidates;

    @Inject
    AutoPostCandidateSourceRepository sources;

    @Inject
    AutoPostAgentService postAgentService;

    @Inject
    AutoPostAccessPolicy accessPolicy;

    @Inject
    DomainMetrics metrics;

    @Transactional
    public AutoPostRun selectCandidateForDrafting(UUID runId, UUID candidateId) {
        AutoPostRun run = requireRunForUpdate(runId);
        if (isExistingSelection(run, candidateId)) {
            return run;
        }

        requireSelectionAvailable(run);
        AutoPostCandidate candidate = requireCandidate(candidateId, runId);
        UserAccessDto official = accessPolicy.requireOfficialAccount();
        UUID draftId = startPostAgentDraft(candidate, official.userId());

        run.markDrafting(candidateId, draftId);
        runs.flush();
        return run;
    }

    @Transactional
    public AutoPostRun retryFailedDraft(UUID runId) {
        AutoPostRun run = requireRunForUpdate(runId);
        requireFailedDraft(run);
        UserAccessDto official = accessPolicy.requireOfficialAccount();
        UUID retriedDraftId = retryPostAgentDraft(
                run.getPepperDraftId(), official.userId());
        run.markDrafting(run.getSelectedCandidateId(), retriedDraftId);
        runs.flush();
        return run;
    }

    public AutoPostRun synchronizeDraft(AutoPostRun run) {
        if (!isDraftGenerationPending(run)) {
            return run;
        }

        long synchronizationStarted = System.nanoTime();
        UserAccessDto official = accessPolicy.requireOfficialAccount();
        AutoPostAgentDraftDto draft = readDraftStatus(run.getPepperDraftId(), official.userId());
        if (draftGenerationFailed(draft)) {
            String faultCode = draftFailureCode(draft);
            recordOperation("draftSynchronization", "fault", "dependency",
                    faultCode, synchronizationStarted);
            AutoPostLog.failed("draftSynchronization", "post_agent_generation", "dependency",
                    faultCode, null);
            return markDraftFailed(run.getId(), faultCode);
        }
        if (draftGenerationCompleted(draft)) {
            recordOperation("draftSynchronization", "success", "none", "none",
                    synchronizationStarted);
            AutoPostLog.succeeded("draftSynchronization", "post_agent_generation");
            return markDraftReady(run.getId());
        }
        return run;
    }

    private AutoPostRun requireRunForUpdate(UUID runId) {
        return runs.findForUpdate(runId).orElseThrow(AutoPostApiException::runMissing);
    }

    private static boolean isExistingSelection(AutoPostRun run, UUID candidateId) {
        return candidateId.equals(run.getSelectedCandidateId()) && run.getPepperDraftId() != null;
    }

    private static void requireSelectionAvailable(AutoPostRun run) {
        if (run.getStatus() != AutoPostRunStatus.CANDIDATES_READY
                || run.getSelectedCandidateId() != null) {
            throw AutoPostApiException.selectionConflict();
        }
    }

    private static void requireFailedDraft(AutoPostRun run) {
        if (run.getStatus() != AutoPostRunStatus.FAILED
                || run.getSelectedCandidateId() == null
                || run.getPepperDraftId() == null) {
            throw AutoPostApiException.draftRetryConflict();
        }
    }

    private AutoPostCandidate requireCandidate(UUID candidateId, UUID runId) {
        return candidates.findInRun(candidateId, runId)
                .orElseThrow(AutoPostApiException::candidateMissing);
    }

    private UUID startPostAgentDraft(AutoPostCandidate candidate, long officialUserId) {
        String prompt = draftPrompt(candidate, sources.listByCandidate(candidate.getId()));
        long started = System.nanoTime();
        AutoPostLog.started("postAgentHandoff", "post_agent_handoff");
        try {
            UUID draftId = postAgentService.startForPublisher(officialUserId, prompt);
            recordOperation("postAgentHandoff", "success", "none", "none", started);
            AutoPostLog.succeeded("postAgentHandoff", "post_agent_handoff");
            return draftId;
        } catch (RuntimeException error) {
            recordOperation("postAgentHandoff", "fault", "dependency",
                    "AUTO_POST_HANDOFF_FAILED", started);
            AutoPostLog.failed("postAgentHandoff", "post_agent_handoff", "dependency",
                    "AUTO_POST_HANDOFF_FAILED", error);
            throw error;
        }
    }

    private UUID retryPostAgentDraft(UUID failedDraftId, long officialUserId) {
        long started = System.nanoTime();
        AutoPostLog.started("postAgentRetry", "post_agent_retry");
        try {
            UUID retriedDraftId = postAgentService.retryForPublisher(
                    failedDraftId, officialUserId);
            recordOperation("postAgentRetry", "success", "none", "none", started);
            AutoPostLog.succeeded("postAgentRetry", "post_agent_retry");
            return retriedDraftId;
        } catch (RuntimeException error) {
            recordOperation("postAgentRetry", "fault", "dependency",
                    "AUTO_POST_DRAFT_RETRY_FAILED", started);
            AutoPostLog.failed("postAgentRetry", "post_agent_retry", "dependency",
                    "AUTO_POST_DRAFT_RETRY_FAILED", error);
            throw error;
        }
    }

    private AutoPostAgentDraftDto readDraftStatus(UUID draftId, long officialUserId) {
        long started = System.nanoTime();
        try {
            AutoPostAgentDraftDto draft = postAgentService.getForPublisher(draftId, officialUserId)
                    .orElse(null);
            recordOperation("draftStatusCheck", "success", "none", "none", started);
            return draft;
        } catch (RuntimeException error) {
            recordOperation("draftStatusCheck", "fault", "dependency",
                    "AUTO_POST_DRAFT_STATUS_READ_FAILED", started);
            AutoPostLog.failed("draftStatusCheck", "post_agent_draft_read", "dependency",
                    "AUTO_POST_DRAFT_STATUS_READ_FAILED", error);
            throw error;
        }
    }

    private static boolean isDraftGenerationPending(AutoPostRun run) {
        return run.getStatus() == AutoPostRunStatus.DRAFTING && run.getPepperDraftId() != null;
    }

    private static boolean draftGenerationFailed(AutoPostAgentDraftDto draft) {
        return draft == null
                || draft.status() == PepperDraftStatus.FAILED
                || (draft.status() == PepperDraftStatus.FINISHED
                && (!Boolean.TRUE.equals(draft.success()) || draft.content() == null));
    }

    private static boolean draftGenerationCompleted(AutoPostAgentDraftDto draft) {
        return draft.status() == PepperDraftStatus.FINISHED
                && Boolean.TRUE.equals(draft.success())
                && draft.content() != null;
    }

    private static String draftFailureCode(AutoPostAgentDraftDto draft) {
        if (draft != null
                && "AGENT_PROVIDER_RESPONSE_TOO_LARGE".equals(draft.errorCode())) {
            return "AUTO_POST_MODEL_RESPONSE_TOO_LARGE";
        }
        return "AUTO_POST_DRAFT_FAILED";
    }

    private AutoPostRun markDraftFailed(UUID runId, String faultCode) {
        return QuarkusTransaction.requiringNew().call(() -> {
            AutoPostRun run = requireRunForUpdate(runId);
            if (run.getStatus() == AutoPostRunStatus.DRAFTING) {
                run.markDraftFailed(faultCode, draftFailureMessage(faultCode));
                runs.flush();
            }
            return run;
        });
    }

    private static String draftFailureMessage(String faultCode) {
        if ("AUTO_POST_MODEL_RESPONSE_TOO_LARGE".equals(faultCode)) {
            return "The model response was too large and was rejected. Retry this draft.";
        }
        return "Post agent could not create the draft. Retry this draft.";
    }

    private AutoPostRun markDraftReady(UUID runId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            AutoPostRun run = requireRunForUpdate(runId);
            if (run.getStatus() == AutoPostRunStatus.DRAFTING) {
                run.markDraftReady();
                runs.flush();
            }
            return run;
        });
    }

    private static String draftPrompt(
            AutoPostCandidate candidate,
            List<AutoPostCandidateSource> candidateSources
    ) {
        String prompt = """
                Create a publication-ready Your Say News draft about this editor-selected current story.

                Headline: %s
                Primary region: %s
                Material development: %s
                Reported at: %s

                Discovery sources:
                %s

                Re-research the subject and follow the normal post-agent sourcing, neutrality,
                voting-question and voting-option rules. Do not treat this discovery brief as a
                substitute for the post agent's own source validation.
                """.formatted(
                candidate.getHeadline(),
                candidate.getRegion(),
                candidate.getSummary(),
                candidate.getPublishedAt(),
                formatSources(candidateSources)).trim();
        return boundPrompt(prompt);
    }

    private static String formatSources(List<AutoPostCandidateSource> sources) {
        return sources.stream()
                .map(source -> "- %s | %s | %s".formatted(
                        source.getPublisher(), source.getTitle(), source.getUrl()))
                .collect(Collectors.joining("\n"));
    }

    private static String boundPrompt(String prompt) {
        return prompt.length() <= 2_000 ? prompt : prompt.substring(0, 2_000);
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
}
