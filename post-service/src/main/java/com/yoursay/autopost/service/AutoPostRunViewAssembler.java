package com.yoursay.autopost.service;

import com.yoursay.autopost.dto.AutoPostCandidateDto;
import com.yoursay.autopost.dto.AutoPostDraftDto;
import com.yoursay.autopost.dto.AutoPostRunDto;
import com.yoursay.autopost.dto.AutoPostSourceDto;
import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.autopost.model.AutoPostCandidateRepository;
import com.yoursay.autopost.model.AutoPostCandidateSource;
import com.yoursay.autopost.model.AutoPostCandidateSourceRepository;
import com.yoursay.autopost.model.AutoPostRun;
import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.dto.AutoPostAgentDraftDto;
import com.yoursay.posts.postagent.dto.PepperPostDraftDto;
import com.yoursay.user.user.dto.UserAccessDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Builds the public admin representation without exposing internal account identifiers. */
@ApplicationScoped
public class AutoPostRunViewAssembler {

    @Inject
    AutoPostCandidateRepository candidates;

    @Inject
    AutoPostCandidateSourceRepository sources;

    @Inject
    AutoPostAgentService postAgentService;

    @Inject
    AutoPostAccessPolicy accessPolicy;

    public AutoPostRunDto toDto(AutoPostRun run) {
        List<AutoPostCandidate> runCandidates = candidates.listByRun(run.getId());
        Map<UUID, List<AutoPostCandidateSource>> sourcesByCandidate =
                groupSourcesByCandidate(runCandidates);
        List<AutoPostCandidateDto> candidateDtos = runCandidates.stream()
                .map(candidate -> toCandidateDto(candidate, sourcesByCandidate))
                .toList();

        return new AutoPostRunDto(
                run.getId(),
                run.getStatus(),
                run.getWindowStart(),
                run.getWindowEnd(),
                candidateDtos,
                run.getSelectedCandidateId(),
                run.getPepperDraftId(),
                draftDto(run),
                run.getPublishedPostId(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private Map<UUID, List<AutoPostCandidateSource>> groupSourcesByCandidate(
            List<AutoPostCandidate> runCandidates
    ) {
        List<UUID> candidateIds = runCandidates.stream()
                .map(AutoPostCandidate::getId)
                .toList();
        return sources.listByCandidates(candidateIds).stream()
                .collect(Collectors.groupingBy(AutoPostCandidateSource::getCandidateId));
    }

    private static AutoPostCandidateDto toCandidateDto(
            AutoPostCandidate candidate,
            Map<UUID, List<AutoPostCandidateSource>> sourcesByCandidate
    ) {
        List<AutoPostSourceDto> sourceDtos = sourcesByCandidate
                .getOrDefault(candidate.getId(), List.of())
                .stream()
                .map(AutoPostRunViewAssembler::toSourceDto)
                .toList();
        return new AutoPostCandidateDto(
                candidate.getId(),
                candidate.getRank(),
                candidate.getRegion(),
                candidate.getHeadline(),
                candidate.getSummary(),
                candidate.getPublishedAt(),
                sourceDtos);
    }

    private AutoPostDraftDto draftDto(AutoPostRun run) {
        if (run.getPepperDraftId() == null) {
            return null;
        }
        UserAccessDto official = accessPolicy.requireOfficialAccount();
        return postAgentService.getForPublisher(run.getPepperDraftId(), official.userId())
                .filter(draft -> draft.content() != null)
                .map(AutoPostRunViewAssembler::toDraftDto)
                .orElse(null);
    }

    private static AutoPostDraftDto toDraftDto(AutoPostAgentDraftDto draft) {
        PepperPostDraftDto content = draft.content();
        List<AutoPostSourceDto> citations = content.citations().stream()
                .map(source -> new AutoPostSourceDto(
                        source.url(), source.title(), source.publisher()))
                .toList();
        return new AutoPostDraftDto(
                draft.id(),
                content.summary(),
                content.supportQuestion(),
                content.caseFor(),
                content.caseAgainst(),
                content.votingType(),
                content.voteOptions(),
                citations,
                draft.version());
    }

    private static AutoPostSourceDto toSourceDto(AutoPostCandidateSource source) {
        return new AutoPostSourceDto(
                source.getUrl(), source.getTitle(), source.getPublisher());
    }
}
