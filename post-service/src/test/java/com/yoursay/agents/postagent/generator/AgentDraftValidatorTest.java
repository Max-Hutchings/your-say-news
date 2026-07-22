package com.yoursay.agents.postagent.generator;

import com.yoursay.agents.postagent.AgentDraftDto;
import com.yoursay.agents.postagent.AgentSourceDto;
import com.yoursay.agents.postagent.SourcedClaimDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDraftValidatorTest {

    private static final String GOVERNMENT_URL = "https://www.gov.uk/government/publications/policy";
    private static final String RESEARCH_URL = "https://ifs.org.uk/research/policy-analysis";

    private final AgentDraftValidator validator = new AgentDraftValidator();

    @Test
    void claimWithoutAnySourceFailsClosed() {
        AgentDraftDto draft = validDraft();
        AgentDraftDto missingClaimSource = new AgentDraftDto(
                List.of(new SourcedClaimDto(draft.summaryClaims().getFirst().text(), List.of())),
                draft.caseForClaims(),
                draft.caseAgainstClaims(),
                draft.supportQuestion(),
                draft.sources(),
                draft.imageBrief(),
                draft.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(missingClaimSource, List.of(GOVERNMENT_URL, RESEARCH_URL)));

        assertEquals("AGENT_INVALID_PROVIDER_OUTPUT", error.code());
        assertFalse(error.retryable());
        assertEquals("summaryClaims claim has no sources", error.getMessage());
    }

    @Test
    void urlCanonicalisationAcceptsCitationWithTrailingSlashAndFragmentDifference() {
        validator.validate(validDraft(),
                List.of(GOVERNMENT_URL + "/", RESEARCH_URL + "#methodology"));
    }

    @Test
    void nonHttpSourceIsRejectedEvenWhenProviderListsIt() {
        String unsafe = "file:///tmp/research.txt";
        AgentDraftDto draft = new AgentDraftDto(
                List.of(new SourcedClaimDto("A policy announcement was published.", List.of(unsafe))),
                List.of(new SourcedClaimDto("Supporters cite implementation benefits.", List.of(RESEARCH_URL))),
                List.of(new SourcedClaimDto("Critics cite delivery risks.", List.of(RESEARCH_URL))),
                "Should the policy be implemented?",
                List.of(
                        new AgentSourceDto(unsafe, "Local file", "Unknown"),
                        new AgentSourceDto(RESEARCH_URL, "Policy analysis", "Institute for Fiscal Studies")),
                "A neutral image of the relevant public institution.",
                "public institution policy reusable image");

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(draft, List.of(unsafe, RESEARCH_URL)));

        assertEquals("Source URL must use HTTP(S): " + unsafe, error.getMessage());
    }

    @Test
    void duplicateCanonicalUrlsDoNotCountAsTwoIndependentSources() {
        AgentDraftDto base = validDraft();
        AgentDraftDto duplicateSources = new AgentDraftDto(
                base.summaryClaims(),
                base.caseForClaims(),
                base.caseAgainstClaims(),
                base.supportQuestion(),
                List.of(
                        new AgentSourceDto(GOVERNMENT_URL, "Policy proposal", "UK Government"),
                        new AgentSourceDto(GOVERNMENT_URL + "/",
                                "The same policy proposal", "UK Government")),
                base.imageBrief(),
                base.imageSearchQuery());

        GenerationException error = assertThrows(GenerationException.class,
                () -> validator.validate(duplicateSources, List.of(GOVERNMENT_URL, RESEARCH_URL)));

        assertEquals("Draft must contain at least two distinct sources", error.getMessage());
    }

    private static AgentDraftDto validDraft() {
        return new AgentDraftDto(
                List.of(new SourcedClaimDto(
                        "The government published a proposal with a defined implementation timetable.",
                        List.of(GOVERNMENT_URL))),
                List.of(new SourcedClaimDto(
                        "Supporters argue the proposal could improve access to the service.",
                        List.of(GOVERNMENT_URL, RESEARCH_URL))),
                List.of(new SourcedClaimDto(
                        "Critics argue delivery costs and capacity assumptions remain uncertain.",
                        List.of(RESEARCH_URL))),
                "Should the government implement this proposal?",
                List.of(
                        new AgentSourceDto(GOVERNMENT_URL, "Policy proposal", "UK Government"),
                        new AgentSourceDto(RESEARCH_URL, "Policy analysis", "Institute for Fiscal Studies")),
                "A neutral image of the responsible public institution, without staged supporters or opponents.",
                "responsible UK public institution reusable licensed photograph");
    }
}
