package com.yoursay.autopost.service;

import com.yoursay.autopost.AutoPostRunStatus;
import com.yoursay.autopost.AutoPostService;
import com.yoursay.autopost.dto.AutoPostCandidateDto;
import com.yoursay.autopost.dto.AutoPostDraftDto;
import com.yoursay.autopost.dto.AutoPostEventDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import com.yoursay.autopost.dto.AutoPostSourceDto;
import com.yoursay.autopost.error.AutoPostApiException;
import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.autopost.model.AutoPostCandidateRepository;
import com.yoursay.autopost.model.AutoPostCandidateSource;
import com.yoursay.autopost.model.AutoPostCandidateSourceRepository;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.autopost.model.AutoPostRunRepository;
import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.PepperDraftStatus;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.PepperDraftDto;
import com.yoursay.posts.postagent.dto.PepperPostDraftDto;
import com.yoursay.posts.PostService;
import com.yoursay.posts.dto.CreatePostRequest;
import com.yoursay.posts.dto.PostCreationProvenance;
import com.yoursay.posts.dto.PostDto;
import com.yoursay.posts.dto.PostSourceDto;
import com.yoursay.user.user.AccountType;
import com.yoursay.user.user.PublisherStatus;
import com.yoursay.user.user.YourSayUserService;
import com.yoursay.user.user.dto.UserAccessDto;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.yoursay.observability.DomainMetrics;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@ApplicationScoped
public class AutoPostServiceImpl implements AutoPostService {

    @Inject
    AutoPostRunRepository runs;

    @Inject
    AutoPostCandidateRepository candidates;

    @Inject
    AutoPostCandidateSourceRepository sources;

    @Inject
    YourSayUserService userService;

    @Inject
    AutoPostAgentService postAgentService;

    @Inject
    PostService postService;

    @Inject
    DomainMetrics metrics;

    @ConfigProperty(name = "autopost.agent.prompt-version", defaultValue = "top-stories-v1")
    String promptVersion;

    @ConfigProperty(name = "autopost.official-handle", defaultValue = "yoursay")
    String officialHandle;

    @Override
    @Transactional
    public AutoPostRunDto start(String administratorEmail) {
        UserAccessDto administrator = requireAdministrator(administratorEmail);
        Instant windowEnd = Instant.now();
        AutoPostRun run = new AutoPostRun(administrator.userId(),
                windowEnd.minus(24, ChronoUnit.HOURS), windowEnd, promptVersion);
        runs.persist(run);
        runs.flush();
        return toDto(run);
    }

    @Override
    public List<AutoPostRunDto> list(String administratorEmail) {
        requireAdministrator(administratorEmail);
        return runs.listRecent().stream().map(this::synchronizeDraft).map(this::toDto).toList();
    }

    @Override
    public AutoPostRunDto get(UUID runId, String administratorEmail) {
        requireAdministrator(administratorEmail);
        return toDto(synchronizeDraft(
                runs.findByIdOptional(runId).orElseThrow(AutoPostApiException::runMissing)));
    }

    @Override
    @Transactional
    public AutoPostRunDto select(UUID runId, UUID candidateId, String administratorEmail) {
        requireAdministrator(administratorEmail);
        AutoPostRun run = runs.findForUpdate(runId).orElseThrow(AutoPostApiException::runMissing);
        if (candidateId.equals(run.getSelectedCandidateId()) && run.getPepperDraftId() != null) {
            return toDto(run);
        }
        if (run.getStatus() != AutoPostRunStatus.CANDIDATES_READY
                || run.getSelectedCandidateId() != null) {
            throw AutoPostApiException.selectionConflict();
        }
        AutoPostCandidate candidate = candidates.findInRun(candidateId, runId)
                .orElseThrow(AutoPostApiException::candidateMissing);
        UserAccessDto official = requireOfficialAccount();
        String prompt = draftPrompt(candidate, sources.listByCandidate(candidateId));
        long handoffStarted = System.nanoTime();
        UUID pepperDraftId;
        try {
            pepperDraftId = postAgentService.startForPublisher(official.userId(), prompt);
            metrics.recordOperation("autopost", "postAgentHandoff", "success", "none", "none",
                    System.nanoTime() - handoffStarted);
        } catch (RuntimeException error) {
            metrics.recordOperation("autopost", "postAgentHandoff", "fault", "dependency",
                    "AUTO_POST_HANDOFF_FAILED", System.nanoTime() - handoffStarted);
            throw error;
        }
        run.markDrafting(candidateId, pepperDraftId);
        runs.flush();
        return toDto(run);
    }

    @Override
    public AutoPostRunDto approve(UUID runId, String administratorEmail) {
        requireAdministrator(administratorEmail);
        AutoPostRun synchronizedRun = synchronizeDraft(
                runs.findByIdOptional(runId).orElseThrow(AutoPostApiException::runMissing));
        if (synchronizedRun.getStatus() == AutoPostRunStatus.PUBLISHED) {
            return toDto(synchronizedRun);
        }
        UserAccessDto official = requireOfficialAccount();
        ApprovalWork work = QuarkusTransaction.requiringNew().call(() -> {
            AutoPostRun locked = runs.findForUpdate(runId)
                    .orElseThrow(AutoPostApiException::runMissing);
            if (locked.getStatus() != AutoPostRunStatus.DRAFT_READY
                    || locked.getPepperDraftId() == null
                    || locked.getSelectedCandidateId() == null) {
                throw AutoPostApiException.approvalConflict();
            }
            locked.markPublishing();
            runs.flush();
            return new ApprovalWork(locked.getId(), locked.getSelectedCandidateId(),
                    locked.getPepperDraftId(), official.userId());
        });

        long publicationStarted = System.nanoTime();
        try {
            PepperDraftDto draft = postAgentService
                    .getForPublisher(work.draftId(), work.publisherUserId())
                    .orElseThrow(AutoPostApiException::approvalConflict);
            if (draft.status() != PepperDraftStatus.FINISHED || !Boolean.TRUE.equals(draft.success())
                    || draft.content() == null) {
                throw AutoPostApiException.approvalConflict();
            }
            AgentPublicationDto provenance = postAgentService.preparePublicationForPublisher(
                    work.draftId(), work.publisherUserId(), draft.content().citations());
            AutoPostCandidate candidate = candidates.findInRun(work.candidateId(), work.runId())
                    .orElseThrow(AutoPostApiException::candidateMissing);
            PostDto post = postService.createForPublisher(
                            work.publisherUserId(),
                            publicationRequest(candidate, draft.content(), work.draftId()),
                            new PostCreationProvenance(provenance.draftId(), provenance.sources().stream()
                                    .map(source -> new PostSourceDto(
                                            source.url(), source.title(), source.publisher()))
                                    .toList()))
                    .await().atMost(Duration.ofSeconds(30));
            postAgentService.markPublished(work.draftId(), post.id());
            AutoPostRun published = QuarkusTransaction.requiringNew().call(() -> {
                AutoPostRun locked = runs.findForUpdate(work.runId())
                        .orElseThrow(AutoPostApiException::runMissing);
                locked.markPublished(post.id());
                runs.flush();
                return locked;
            });
            metrics.recordOperation("autopost", "publication", "success", "none", "none",
                    System.nanoTime() - publicationStarted);
            return toDto(published);
        } catch (AutoPostApiException error) {
            restoreDraftReady(work.runId());
            metrics.recordOperation("autopost", "publication", "error", "workflow",
                    error.errorCode(), System.nanoTime() - publicationStarted);
            throw error;
        } catch (RuntimeException error) {
            restoreDraftReady(work.runId());
            metrics.recordOperation("autopost", "publication", "fault", "application",
                    "AUTO_POST_PUBLICATION_FAILED", System.nanoTime() - publicationStarted);
            throw AutoPostApiException.publicationFailed();
        }
    }

    @Override
    public Multi<AutoPostEventDto> events(UUID runId, String administratorEmail) {
        requireAdministrator(administratorEmail);
        long streamStarted = System.nanoTime();
        AtomicBoolean streamOutcomeRecorded = new AtomicBoolean();
        Multi<AutoPostRunDto> updates = Multi.createBy().repeating().uni(() -> Uni.createFrom()
                        .item(() -> get(runId, administratorEmail))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool()))
                .withDelay(Duration.ofSeconds(1))
                .until(run -> run.status().streamTerminal());
        return updates.onCompletion().switchTo(() -> Multi.createFrom().uni(Uni.createFrom()
                        .item(() -> get(runId, administratorEmail))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())))
                .map(this::toMeasuredSseEvent)
                .onCompletion().invoke(() -> recordStreamOutcome(
                        streamOutcomeRecorded, "success", "none", "none", streamStarted))
                .onCancellation().invoke(() -> recordStreamOutcome(
                        streamOutcomeRecorded, "success", "none", "none", streamStarted))
                .onFailure().invoke(() -> recordStreamOutcome(
                        streamOutcomeRecorded, "fault", "application", "AUTO_POST_SSE_FAILED", streamStarted));
    }

    private void recordStreamOutcome(AtomicBoolean recorded, String outcome, String faultType,
                                     String faultCode, long started) {
        if (recorded.compareAndSet(false, true)) {
            metrics.recordOperation("autopost", "sseLifetime", outcome, faultType, faultCode,
                    System.nanoTime() - started);
        }
    }

    private AutoPostEventDto toMeasuredSseEvent(AutoPostRunDto run) {
        long started = System.nanoTime();
        try {
            AutoPostEventDto event = new AutoPostEventDto(run);
            metrics.recordOperation("autopost", "sseEvent", "success", "none", "none",
                    System.nanoTime() - started);
            return event;
        } catch (RuntimeException error) {
            metrics.recordOperation("autopost", "sseEvent", "fault", "application",
                    "AUTO_POST_SSE_EVENT_FAILED", System.nanoTime() - started);
            throw error;
        }
    }

    private AutoPostRun synchronizeDraft(AutoPostRun run) {
        if (run.getStatus() != AutoPostRunStatus.DRAFTING || run.getPepperDraftId() == null) {
            return run;
        }
        UserAccessDto official = requireOfficialAccount();
        PepperDraftDto draft = postAgentService
                .getForPublisher(run.getPepperDraftId(), official.userId())
                .orElse(null);
        if (draft == null || draft.status() == PepperDraftStatus.FAILED) {
            return QuarkusTransaction.requiringNew().call(() -> {
                AutoPostRun locked = runs.findForUpdate(run.getId())
                        .orElseThrow(AutoPostApiException::runMissing);
                if (locked.getStatus() == AutoPostRunStatus.DRAFTING) {
                    locked.markDraftFailed();
                    runs.flush();
                }
                return locked;
            });
        }
        if (draft.status() != PepperDraftStatus.FINISHED || !Boolean.TRUE.equals(draft.success())) {
            return run;
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            AutoPostRun locked = runs.findForUpdate(run.getId())
                    .orElseThrow(AutoPostApiException::runMissing);
            if (locked.getStatus() == AutoPostRunStatus.DRAFTING) {
                locked.markDraftReady();
                runs.flush();
            }
            return locked;
        });
    }

    private UserAccessDto requireOfficialAccount() {
        UserAccessDto official = userService.getAccessByHandle(officialHandle);
        if (official == null || !official.canPublish()
                || official.accountType() != AccountType.OFFICIAL
                || official.publisherStatus() != PublisherStatus.ACTIVE) {
            throw AutoPostApiException.officialAccountUnavailable();
        }
        return official;
    }

    private void restoreDraftReady(UUID runId) {
        QuarkusTransaction.requiringNew().run(() -> {
            AutoPostRun run = runs.findForUpdate(runId)
                    .orElseThrow(AutoPostApiException::runMissing);
            if (run.getStatus() == AutoPostRunStatus.PUBLISHING) {
                run.markPublicationReadyForRetry("AUTO_POST_PUBLICATION_FAILED",
                        "The approved post could not be published. Try again.");
                runs.flush();
            }
        });
    }

    private static CreatePostRequest publicationRequest(
            AutoPostCandidate candidate, PepperPostDraftDto draft, UUID draftId) {
        return new CreatePostRequest(
                draft.summary(), draft.supportQuestion(), draft.caseFor(), draft.caseAgainst(),
                switch (candidate.getRegion()) {
                    case UK -> "GB";
                    case US -> "US";
                    case GLOBAL -> "GLOBAL";
                },
                draft.votingType(),
                draft.voteOptions().stream().map(CreatePostRequest.VoteOption::new).toList(),
                List.of(), List.of(), draftId,
                draft.citations().stream().map(source -> new CreatePostRequest.Citation(
                        source.url(), source.title(), source.publisher())).toList());
    }

    private UserAccessDto requireAdministrator(String email) {
        if (!userService.hasActiveAdminAccess(email)) {
            throw AutoPostApiException.adminAccessRequired();
        }
        UserAccessDto access = userService.getAccessByEmail(email);
        if (access == null) {
            throw AutoPostApiException.adminAccessRequired();
        }
        return access;
    }

    private AutoPostRunDto toDto(AutoPostRun run) {
        List<AutoPostCandidate> runCandidates = candidates.listByRun(run.getId());
        Map<UUID, List<AutoPostCandidateSource>> byCandidate = sources
                .listByCandidates(runCandidates.stream().map(AutoPostCandidate::getId).toList())
                .stream().collect(Collectors.groupingBy(AutoPostCandidateSource::getCandidateId));
        AutoPostDraftDto draft = draftDto(run);
        return new AutoPostRunDto(
                run.getId(), run.getStatus(), run.getWindowStart(), run.getWindowEnd(),
                runCandidates.stream().map(candidate -> new AutoPostCandidateDto(
                        candidate.getId(), candidate.getRank(), candidate.getRegion(),
                        candidate.getHeadline(), candidate.getSummary(), candidate.getPublishedAt(),
                        byCandidate.getOrDefault(candidate.getId(), List.of()).stream()
                                .map(source -> new AutoPostSourceDto(
                                        source.getUrl(), source.getTitle(), source.getPublisher()))
                                .toList())).toList(),
                run.getSelectedCandidateId(), run.getPepperDraftId(), draft, run.getPublishedPostId(),
                run.getErrorCode(), run.getErrorMessage(), run.getCreatedAt(), run.getUpdatedAt());
    }

    private AutoPostDraftDto draftDto(AutoPostRun run) {
        if (run.getPepperDraftId() == null) {
            return null;
        }
        UserAccessDto official = requireOfficialAccount();
        return postAgentService.getForPublisher(run.getPepperDraftId(), official.userId())
                .filter(draft -> draft.content() != null)
                .map(draft -> {
                    PepperPostDraftDto content = draft.content();
                    return new AutoPostDraftDto(
                            draft.id(), content.summary(), content.supportQuestion(),
                            content.caseFor(), content.caseAgainst(), content.votingType(),
                            content.voteOptions(), content.citations().stream()
                                    .map(source -> new AutoPostSourceDto(
                                            source.url(), source.title(), source.publisher()))
                                    .toList(), draft.version());
                }).orElse(null);
    }

    private static String draftPrompt(AutoPostCandidate candidate,
                                      List<AutoPostCandidateSource> sources) {
        String sourceLines = sources.stream()
                .map(source -> "- %s | %s | %s".formatted(
                        source.getPublisher(), source.getTitle(), source.getUrl()))
                .collect(Collectors.joining("\n"));
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
                """.formatted(candidate.getHeadline(), candidate.getRegion(), candidate.getSummary(),
                candidate.getPublishedAt(), sourceLines).trim();
        return prompt.length() <= 2_000 ? prompt : prompt.substring(0, 2_000);
    }

    private record ApprovalWork(UUID runId, UUID candidateId, UUID draftId, long publisherUserId) {
    }
}
