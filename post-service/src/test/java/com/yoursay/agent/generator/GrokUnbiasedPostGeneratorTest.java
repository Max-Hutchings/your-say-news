package com.yoursay.agent.generator;

import com.yoursay.agent.AgentDraftDto;
import com.yoursay.agent.AgentSourceDto;
import com.yoursay.agent.SourcedClaimDto;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrokUnbiasedPostGeneratorTest {

    private static final String STATISTICS_URL =
            "https://www.ons.gov.uk/releases/electiondata";
    private static final String PARLIAMENT_URL =
            "https://www.parliament.uk/bills/voting-age";

    private PepperAiClient client;
    private GrokUnbiasedPostGenerator generator;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(PepperAiClient.class);
        generator = new GrokUnbiasedPostGenerator();
        generator.client = client;
        generator.validator = new AgentDraftValidator();
        generator.apiKey = "xai-test-key";
        generator.model = "grok-4.3";
    }

    @Test
    void generateReturnsTheLangChain4jDraftAfterSourceValidation() {
        AgentDraftDto draft = validDraft(STATISTICS_URL, PARLIAMENT_URL);
        Mockito.when(client.research(Mockito.anyString())).thenReturn(new PepperAiResponse(
                draft,
                List.of(STATISTICS_URL, PARLIAMENT_URL),
                "grok-4.3",
                "resp_uk_voting_age"
        ));

        GenerationResult result = generator.generate(
                "Explain the proposal to lower the UK voting age and the strongest arguments.");

        assertEquals("grok-4.3", result.model());
        assertEquals("resp_uk_voting_age", result.providerResponseId());
        assertEquals("Should the UK voting age be lowered to 16?", result.draft().supportQuestion());
        assertEquals(
                "The proposal would extend voting rights to 16- and 17-year-olds.",
                result.draft().summaryClaims().getFirst().text());
        assertEquals(PARLIAMENT_URL,
                result.draft().caseForClaims().getFirst().sourceUrls().getFirst());
        assertEquals(2, result.draft().sources().size());
        Mockito.verify(client).research(
                "Explain the proposal to lower the UK voting age and the strongest arguments.");
    }

    @Test
    void generateRejectsClaimUrlThatGrokDidNotReturnAsASearchCitation() {
        String fabricated = "https://fabricated.example/evidence";
        Mockito.when(client.research(Mockito.anyString())).thenReturn(new PepperAiResponse(
                validDraft(STATISTICS_URL, fabricated),
                List.of(STATISTICS_URL, PARLIAMENT_URL),
                "grok-4.3",
                "resp_untrusted_source"
        ));

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover voting age."));

        assertEquals("AGENT_INVALID_PROVIDER_OUTPUT", error.code());
        assertFalse(error.retryable());
        assertTrue(error.getMessage().contains("not returned in provider citations"));
    }

    @Test
    void generateMapsLangChain4jRateLimitToRetryableFailure() {
        Mockito.when(client.research(Mockito.anyString()))
                .thenThrow(new RateLimitException("provider-specific account detail"));

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_UNAVAILABLE", error.code());
        assertTrue(error.retryable());
        assertEquals("Grok request failed and may be retried", error.getMessage());
    }

    @Test
    void generateMapsLangChain4jInvalidRequestToFinalFailure() {
        Mockito.when(client.research(Mockito.anyString()))
                .thenThrow(new InvalidRequestException("provider-specific request detail"));

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("Grok rejected the request or returned invalid output", error.getMessage());
    }

    @Test
    void generateFailsBeforeCallingLangChain4jWhenApiKeyIsMissing() {
        generator.apiKey = "__not_configured__";

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_NOT_CONFIGURED", error.code());
        assertFalse(error.retryable());
        Mockito.verifyNoInteractions(client);
    }

    private static AgentDraftDto validDraft(String statisticsUrl, String parliamentUrl) {
        return new AgentDraftDto(
                List.of(new SourcedClaimDto(
                        "The proposal would extend voting rights to 16- and 17-year-olds.",
                        List.of(statisticsUrl, parliamentUrl))),
                List.of(new SourcedClaimDto(
                        "Supporters argue that people affected by public policy should gain a vote earlier.",
                        List.of(parliamentUrl))),
                List.of(new SourcedClaimDto(
                        "Opponents argue that the existing age threshold remains a clearer national standard.",
                        List.of(statisticsUrl))),
                "Should the UK voting age be lowered to 16?",
                List.of(
                        new AgentSourceDto(statisticsUrl, "Election participation data",
                                "Office for National Statistics"),
                        new AgentSourceDto(parliamentUrl, "Voting age bill", "UK Parliament")
                ),
                "A neutral photograph of a polling station sign without identifiable voters.",
                "UK polling station sign reusable licensed image"
        );
    }
}
