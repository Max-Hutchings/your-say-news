package com.yoursay.autopost.service;

import com.yoursay.platform.ai.AiConfig;
import com.yoursay.autopost.AutoPostService;
import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import com.yoursay.autopost.error.AutoPostApiException;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.autopost.model.AutoPostRunRepository;
import com.yoursay.user.user.dto.UserAccessDto;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Coordinates the public auto-post contract through focused internal workflows. */
@ApplicationScoped
public class AutoPostServiceImpl implements AutoPostService {

    @Inject
    AutoPostRunRepository runs;

    @Inject
    AutoPostAccessPolicy accessPolicy;

    @Inject
    AutoPostDraftWorkflow draftWorkflow;

    @Inject
    AutoPostPublicationWorkflow publicationWorkflow;

    @Inject
    AutoPostRunViewAssembler viewAssembler;

    @Inject
    AutoPostEventStream eventStream;

    @Inject
    AiConfig aiConfig;

    @Override
    @Transactional
    public AutoPostRunDto startDiscoveryRun(String administratorEmail) {
        UserAccessDto administrator = accessPolicy.requireAdministrator(administratorEmail);
        AutoPostRun run = createDiscoveryRun(administrator.userId());
        runs.persist(run);
        runs.flush();
        return viewAssembler.toDto(run);
    }

    @Override
    public List<AutoPostRunDto> listRecentRuns(String administratorEmail) {
        accessPolicy.requireAdministrator(administratorEmail);
        return runs.listRecent().stream()
                .map(draftWorkflow::synchronizeDraft)
                .map(viewAssembler::toDto)
                .toList();
    }

    @Override
    public AutoPostRunDto getRun(UUID runId, String administratorEmail) {
        accessPolicy.requireAdministrator(administratorEmail);
        AutoPostRun run = draftWorkflow.synchronizeDraft(requireRun(runId));
        return viewAssembler.toDto(run);
    }

    @Override
    public AutoPostRunDto selectCandidateForDrafting(
            UUID runId,
            UUID candidateId,
            String administratorEmail
    ) {
        accessPolicy.requireAdministrator(administratorEmail);
        return viewAssembler.toDto(draftWorkflow.selectCandidateForDrafting(runId, candidateId));
    }

    @Override
    public AutoPostRunDto approveAndPublishDraft(UUID runId, String administratorEmail) {
        accessPolicy.requireAdministrator(administratorEmail);
        return viewAssembler.toDto(publicationWorkflow.approveAndPublishDraft(runId));
    }

    @Override
    public Multi<AutoPostEventDto> streamRunEvents(UUID runId, String administratorEmail) {
        accessPolicy.requireAdministrator(administratorEmail);
        return eventStream.openRunEventStream(() -> getRun(runId, administratorEmail));
    }

    private AutoPostRun createDiscoveryRun(long administratorUserId) {
        Instant windowEnd = Instant.now();
        return new AutoPostRun(
                administratorUserId,
                windowEnd.minus(24, ChronoUnit.HOURS),
                windowEnd,
                aiConfig.autoPost().promptVersion());
    }

    private AutoPostRun requireRun(UUID runId) {
        return runs.findByIdOptional(runId).orElseThrow(AutoPostApiException::runMissing);
    }
}
