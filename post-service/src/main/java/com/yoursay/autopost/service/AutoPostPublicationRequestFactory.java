package com.yoursay.autopost.service;

import com.yoursay.autopost.model.AutoPostCandidate;
import com.yoursay.posts.dto.CreatePostRequest;
import com.yoursay.posts.dto.PostCreationProvenance;
import com.yoursay.posts.dto.PostSourceDto;
import com.yoursay.posts.postagent.dto.AgentPublicationDto;
import com.yoursay.posts.postagent.dto.PepperPostDraftDto;
import com.yoursay.posts.VotingType;

import java.util.List;
import java.util.UUID;

/** Maps an approved agent draft into the posts domain's publication contracts. */
public final class AutoPostPublicationRequestFactory {

    public static CreatePostRequest createRequest(
            AutoPostCandidate candidate,
            PepperPostDraftDto draft,
            UUID draftId
    ) {
        List<CreatePostRequest.VoteOption> voteOptions = draft.votingType() == VotingType.BINARY
                ? List.of()
                : draft.voteOptions().stream()
                        .map(CreatePostRequest.VoteOption::new)
                        .toList();
        List<CreatePostRequest.Citation> citations = draft.citations().stream()
                .map(source -> new CreatePostRequest.Citation(
                        source.url(), source.title(), source.publisher()))
                .toList();
        return new CreatePostRequest(
                draft.summary(),
                draft.supportQuestion(),
                draft.caseFor(),
                draft.caseAgainst(),
                jurisdiction(candidate),
                draft.votingType(),
                voteOptions,
                List.of(),
                List.of(),
                draftId,
                citations);
    }

    public static PostCreationProvenance createProvenance(AgentPublicationDto provenance) {
        List<PostSourceDto> postSources = provenance.sources().stream()
                .map(source -> new PostSourceDto(
                        source.url(), source.title(), source.publisher()))
                .toList();
        return new PostCreationProvenance(provenance.draftId(), postSources);
    }

    private static String jurisdiction(AutoPostCandidate candidate) {
        return switch (candidate.getRegion()) {
            case UK -> "GB";
            case US -> "US";
            case GLOBAL -> "GLOBAL";
        };
    }

    private AutoPostPublicationRequestFactory() {
    }
}
