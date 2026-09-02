package com.yoursay.posts.postagent.validation;

import com.yoursay.posts.VotingType;
import com.yoursay.posts.postagent.agent.GenerationException;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.AgentVoteOptionDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDraftValidatorTest {

    private final AgentDraftValidator validator = new AgentDraftValidator();

    @Test
    void acceptsPopulatedFieldsWithoutEnforcingPromptWritingRules() {
        AgentDraftDto draft = new AgentDraftDto(
                List.of(new SourcedClaimDto(
                        "A deliberately long claim may contain several sentences. The model-facing prompt owns writing limits.",
                        List.of("file:///model-returned-source"))),
                List.of(new SourcedClaimDto("A supporting claim.", List.of("source-for"))),
                List.of(new SourcedClaimDto("An opposing claim.", List.of("source-against"))),
                "A populated question without enforced punctuation",
                VotingType.MULTIPLE_CHOICE,
                List.of(
                        new AgentVoteOptionDto("Repeated option"),
                        new AgentVoteOptionDto("Repeated option")),
                List.of(new AgentSourceDto(
                        "file:///model-returned-source", "A populated title", "A publisher")),
                "A populated image brief with no custom sentence limit.",
                "populated image query");

        assertDoesNotThrow(() -> validator.validate(draft));
    }

    @Test
    void rejectsAnEmptyTopLevelList() {
        AgentDraftDto draft = validDraft();
        AgentDraftDto missingClaims = new AgentDraftDto(
                List.of(), draft.caseForClaims(), draft.caseAgainstClaims(),
                draft.supportQuestion(), draft.votingType(), draft.voteOptions(), draft.sources(),
                draft.imageBrief(), draft.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(missingClaims));

        assertEquals("AGENT_REQUIRED_FIELD", error.code());
        assertEquals("summaryClaims is required", error.getMessage());
    }

    @Test
    void rejectsABlankNestedClaimField() {
        AgentDraftDto draft = validDraft();
        AgentDraftDto blankClaim = new AgentDraftDto(
                List.of(new SourcedClaimDto("   ", List.of("https://example.com/source"))),
                draft.caseForClaims(), draft.caseAgainstClaims(), draft.supportQuestion(),
                draft.votingType(), draft.voteOptions(), draft.sources(), draft.imageBrief(),
                draft.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(blankClaim));

        assertEquals("AGENT_REQUIRED_FIELD", error.code());
        assertEquals("summaryClaims text is required", error.getMessage());
    }

    @Test
    void rejectsABlankSourceMetadataField() {
        AgentDraftDto draft = validDraft();
        AgentDraftDto blankPublisher = new AgentDraftDto(
                draft.summaryClaims(), draft.caseForClaims(), draft.caseAgainstClaims(),
                draft.supportQuestion(), draft.votingType(), draft.voteOptions(),
                List.of(new AgentSourceDto("https://example.com/source", "Source title", " ")),
                draft.imageBrief(), draft.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(blankPublisher));

        assertEquals("AGENT_REQUIRED_FIELD", error.code());
        assertEquals("sources publisher is required", error.getMessage());
    }

    @Test
    void rejectsABlankVoteOption() {
        AgentDraftDto draft = validDraft();
        AgentDraftDto blankOption = new AgentDraftDto(
                draft.summaryClaims(), draft.caseForClaims(), draft.caseAgainstClaims(),
                draft.supportQuestion(), draft.votingType(),
                List.of(new AgentVoteOptionDto(" ")), draft.sources(), draft.imageBrief(),
                draft.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(blankOption));

        assertEquals("AGENT_REQUIRED_FIELD", error.code());
        assertEquals("voteOptions label is required", error.getMessage());
    }

    private static AgentDraftDto validDraft() {
        String sourceUrl = "https://example.com/source";
        SourcedClaimDto claim = new SourcedClaimDto("A populated claim.", List.of(sourceUrl));
        return new AgentDraftDto(
                List.of(claim),
                List.of(claim),
                List.of(claim),
                "A populated support question?",
                VotingType.BINARY,
                List.of(new AgentVoteOptionDto("Agree"), new AgentVoteOptionDto("Disagree")),
                List.of(new AgentSourceDto(sourceUrl, "Source title", "Publisher")),
                "A populated image brief.",
                "populated image query");
    }
}
