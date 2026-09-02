package com.yoursay.posts.postagent.validation;

import com.yoursay.posts.postagent.agent.GenerationException;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.AgentVoteOptionDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/** Rejects incomplete model output without re-enforcing model-facing writing instructions. */
@ApplicationScoped
public class AgentDraftValidator {

    public void validate(AgentDraftDto draft) {
        require(draft != null, "draft");
        validateClaims("summaryClaims", draft.summaryClaims());
        validateClaims("caseForClaims", draft.caseForClaims());
        validateClaims("caseAgainstClaims", draft.caseAgainstClaims());
        requireText(draft.supportQuestion(), "supportQuestion");
        require(draft.votingType() != null, "votingType");
        validateVoteOptions(draft.voteOptions());
        validateSources(draft.sources());
        requireText(draft.imageBrief(), "imageBrief");
        requireText(draft.imageSearchQuery(), "imageSearchQuery");
    }

    private static void validateClaims(String field, List<SourcedClaimDto> claims) {
        requirePopulated(claims, field);
        for (SourcedClaimDto claim : claims) {
            require(claim != null, field);
            requireText(claim.text(), field + " text");
            requirePopulated(claim.sourceUrls(), field + " sourceUrls");
            claim.sourceUrls().forEach(url -> requireText(url, field + " sourceUrls"));
        }
    }

    private static void validateVoteOptions(List<AgentVoteOptionDto> options) {
        requirePopulated(options, "voteOptions");
        for (AgentVoteOptionDto option : options) {
            require(option != null, "voteOptions");
            requireText(option.label(), "voteOptions label");
        }
    }

    private static void validateSources(List<AgentSourceDto> sources) {
        requirePopulated(sources, "sources");
        for (AgentSourceDto source : sources) {
            require(source != null, "sources");
            requireText(source.url(), "sources url");
            requireText(source.title(), "sources title");
            requireText(source.publisher(), "sources publisher");
        }
    }

    private static void requirePopulated(List<?> values, String field) {
        require(values != null && !values.isEmpty(), field);
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field);
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new GenerationException(
                    "AGENT_REQUIRED_FIELD", field + " is required", false);
        }
    }
}
