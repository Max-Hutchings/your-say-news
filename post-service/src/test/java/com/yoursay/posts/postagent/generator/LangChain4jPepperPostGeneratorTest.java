package com.yoursay.posts.postagent.generator;

import com.yoursay.platform.ai.AiConfig;
import com.yoursay.platform.ai.AiProviderFailureLog;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import com.yoursay.posts.postagent.generator.*;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jPepperPostGeneratorTest {

    private static final String STATISTICS_URL =
            "https://www.ons.gov.uk/releases/electiondata";
    private static final String PARLIAMENT_URL =
            "https://www.parliament.uk/bills/voting-age";

    private PepperAiClient client;
    private LangChain4jPepperPostGenerator generator;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(PepperAiClient.class);
        generator = new LangChain4jPepperPostGenerator();
        generator.client = client;
        generator.validator = new AgentDraftValidator();
        generator.aiConfig = aiConfig("xai-test-key", "grok-4.3");
        generator.providerFailureLog = Mockito.mock(AiProviderFailureLog.class);
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
    void generateUsesConfiguredModelWhenProviderMetadataHasNoModel() {
        AgentDraftDto draft = validDraft(STATISTICS_URL, PARLIAMENT_URL);
        generator.aiConfig = aiConfig("xai-test-key", "gpt-5.6-configured");
        for (String missingModel : new String[]{null, "   "}) {
            Mockito.when(client.research(Mockito.anyString())).thenReturn(new PepperAiResponse(
                    draft,
                    List.of(STATISTICS_URL, PARLIAMENT_URL),
                    missingModel,
                    "resp_without_model"
            ));

            GenerationResult result = generator.generate("Cover voting age.");

            assertEquals("gpt-5.6-configured", result.model());
            assertEquals("resp_without_model", result.providerResponseId());
        }
    }

    @Test
    void generateRejectsClaimUrlThatTheProviderDidNotReturnAsASearchCitation() {
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
        RateLimitException providerFailure = new RateLimitException(
                new HttpException(429, "provider-specific account detail"));
        Mockito.when(client.research(Mockito.anyString()))
                .thenThrow(providerFailure);

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_UNAVAILABLE", error.code());
        assertTrue(error.retryable());
        assertEquals("AI provider request failed and may be retried", error.getMessage());
        assertFalse(error.getMessage().contains("provider-specific account detail"));
        Mockito.verify(generator.providerFailureLog).logNonSuccessResponse(
                AiConfig.Provider.GROK,
                "postagent",
                "generation",
                "AGENT_PROVIDER_UNAVAILABLE",
                providerFailure);
    }

    @Test
    void generateMapsLangChain4jInvalidRequestToFinalFailure() {
        InvalidRequestException providerFailure = new InvalidRequestException(
                new HttpException(400, "provider-specific request detail"));
        Mockito.when(client.research(Mockito.anyString()))
                .thenThrow(providerFailure);

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("AI provider rejected the request or returned invalid output", error.getMessage());
        Mockito.verify(generator.providerFailureLog).logNonSuccessResponse(
                AiConfig.Provider.GROK,
                "postagent",
                "generation",
                "AGENT_PROVIDER_RESPONSE_INVALID",
                providerFailure);
    }

    @Test
    void generateMapsUnexpectedProviderFailureWithoutExposingProviderDetail() {
        Mockito.when(client.research(Mockito.anyString()))
                .thenThrow(new IllegalStateException("api-key=sensitive-provider-detail"));

        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generate("Cover a current policy dispute."));

        assertEquals("AGENT_PROVIDER_UNAVAILABLE", error.code());
        assertTrue(error.retryable());
        assertEquals("AI provider request failed", error.getMessage());
        assertFalse(error.getMessage().contains("sensitive-provider-detail"));
    }

    @Test
    void generateFailsBeforeCallingLangChain4jWhenApiKeyIsMissing() {
        for (String missingKey : new String[]{null, "", "   ", "__not_configured__"}) {
            generator.aiConfig = aiConfig(missingKey, "grok-4.3");
            GenerationException error = assertThrows(GenerationException.class,
                    () -> generator.generate("Cover a current policy dispute."));
            assertEquals("AGENT_PROVIDER_NOT_CONFIGURED", error.code());
            assertFalse(error.retryable());
        }
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

    private static AiConfig aiConfig(String apiKey, String model) {
        return new AiConfig(
                "grok",
                "low",
                apiKey,
                model,
                "test-replica",
                "autopost-key",
                "autopost-model",
                "top-stories-v3",
                "unwrapped-key",
                "unwrapped-model",
                false);
    }
}
