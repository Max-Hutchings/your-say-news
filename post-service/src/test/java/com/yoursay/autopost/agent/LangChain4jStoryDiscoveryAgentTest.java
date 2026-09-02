package com.yoursay.autopost.agent;

import com.yoursay.platform.ai.AiConfig;
import com.yoursay.platform.ai.AiProviderFailureLog;
import com.yoursay.platform.observability.DomainMetrics;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChain4jStoryDiscoveryAgentTest {

    private AutoPostAiClient client;
    private DomainMetrics metrics;
    private LangChain4jStoryDiscoveryAgent agent;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AutoPostAiClient.class);
        metrics = Mockito.mock(DomainMetrics.class);
        agent = new LangChain4jStoryDiscoveryAgent();
        agent.client = client;
        agent.metrics = metrics;
        agent.aiConfig = aiConfig("configured-key");
        agent.providerFailureLog = Mockito.mock(AiProviderFailureLog.class);
    }

    @Test
    void reportsStructuredOutputParsingAtTheExactFailureStage() {
        OutputParsingException parsingFailure = new OutputParsingException(
                "Provider returned a timestamp object instead of an ISO timestamp",
                new IllegalArgumentException("publishedAt"));
        Mockito.when(client.discover(Mockito.any(), Mockito.any())).thenThrow(parsingFailure);

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> agent.discover(
                        Instant.parse("2026-08-22T12:00:00Z"),
                        Instant.parse("2026-08-23T12:00:00Z")));

        assertEquals("AUTO_POST_PROVIDER_RESPONSE_INVALID", error.code());
        assertEquals("structured_output_parsing", error.stage());
        assertEquals("Auto-post discovery provider returned invalid structured output",
                error.getMessage());
        assertFalse(error.getMessage().contains("timestamp object"));
        assertFalse(error.retryable());
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("provider_contract"),
                Mockito.eq("AUTO_POST_PROVIDER_RESPONSE_INVALID"), Mockito.anyLong());
    }

    @Test
    void returnsSuccessfulDiscoveryAndRecordsOneSuccessfulProviderOperation() {
        StoryDiscoveryResult expected = new StoryDiscoveryResult(
                java.util.List.of(), "gpt-5.6", "response-42", java.util.List.of());
        Mockito.when(client.discover(Mockito.any(), Mockito.any())).thenReturn(expected);
        Instant windowStart = Instant.parse("2026-08-22T12:00:00Z");
        Instant windowEnd = Instant.parse("2026-08-23T12:00:00Z");

        StoryDiscoveryResult actual = agent.discover(windowStart, windowEnd);

        assertSame(expected, actual);
        Mockito.verify(client).discover(windowStart, windowEnd);
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("success"),
                Mockito.eq("none"), Mockito.eq("none"), Mockito.anyLong());
    }

    @Test
    void rejectsEveryMissingApiKeyFormBeforeCallingTheProvider() {
        for (String missingKey : new String[]{null, "", "   ", "__not_configured__"}) {
            agent.aiConfig = aiConfig(missingKey);
            AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                    () -> agent.discover(
                            Instant.parse("2026-08-22T12:00:00Z"),
                            Instant.parse("2026-08-23T12:00:00Z")));
            assertEquals("AUTO_POST_PROVIDER_NOT_CONFIGURED", error.code());
            assertEquals("provider_configuration", error.stage());
            assertFalse(error.retryable());
        }
        Mockito.verifyNoInteractions(client);
    }

    @Test
    void preservesAnAlreadyClassifiedDiscoveryFailure() {
        AutoPostDiscoveryException classified = new AutoPostDiscoveryException(
                "AUTO_POST_WEB_SEARCH_FAILED", "dependency", "web_search",
                "Search failed", false, null);
        Mockito.when(client.discover(Mockito.any(), Mockito.any())).thenThrow(classified);

        AutoPostDiscoveryException actual = assertThrows(AutoPostDiscoveryException.class,
                () -> discover());

        assertSame(classified, actual);
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("dependency"), Mockito.eq("AUTO_POST_WEB_SEARCH_FAILED"),
                Mockito.anyLong());
    }

    @Test
    void mapsRetriableProviderFailuresToRetryableDependencyFaults() {
        RateLimitException providerFailure = new RateLimitException(
                new HttpException(429, "provider account detail"));
        Mockito.when(client.discover(Mockito.any(), Mockito.any()))
                .thenThrow(providerFailure);

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> discover());

        assertEquals("AUTO_POST_PROVIDER_UNAVAILABLE", error.code());
        assertEquals("provider_request", error.stage());
        assertEquals("Auto-post discovery provider is unavailable", error.getMessage());
        assertFalse(error.getMessage().contains("provider account detail"));
        assertTrue(error.retryable());
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("dependency"), Mockito.eq("AUTO_POST_PROVIDER_UNAVAILABLE"),
                Mockito.anyLong());
        Mockito.verify(agent.providerFailureLog).logNonSuccessResponse(
                AiConfig.Provider.OPENAI,
                "autopost",
                "providerResearch",
                "AUTO_POST_PROVIDER_UNAVAILABLE",
                providerFailure);
    }

    @Test
    void mapsNonRetriableProviderFailuresToFinalContractFaults() {
        InvalidRequestException providerFailure = new InvalidRequestException(
                new HttpException(400, "provider request detail"));
        Mockito.when(client.discover(Mockito.any(), Mockito.any()))
                .thenThrow(providerFailure);

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> discover());

        assertEquals("AUTO_POST_PROVIDER_RESPONSE_INVALID", error.code());
        assertEquals("provider_response", error.stage());
        assertEquals("Auto-post discovery provider returned invalid output", error.getMessage());
        assertFalse(error.getMessage().contains("provider request detail"));
        assertFalse(error.retryable());
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("provider_contract"),
                Mockito.eq("AUTO_POST_PROVIDER_RESPONSE_INVALID"), Mockito.anyLong());
        Mockito.verify(agent.providerFailureLog).logNonSuccessResponse(
                AiConfig.Provider.OPENAI,
                "autopost",
                "providerResearch",
                "AUTO_POST_PROVIDER_RESPONSE_INVALID",
                providerFailure);
    }

    @Test
    void mapsUnexpectedProcessingFailuresWithoutExposingTheirMessage() {
        Mockito.when(client.discover(Mockito.any(), Mockito.any()))
                .thenThrow(new IllegalStateException("api-key=sensitive"));

        AutoPostDiscoveryException error = assertThrows(AutoPostDiscoveryException.class,
                () -> discover());

        assertEquals("AUTO_POST_PROVIDER_PROCESSING_FAILED", error.code());
        assertEquals("provider_response", error.stage());
        assertEquals("Auto-post discovery response processing failed", error.getMessage());
        assertFalse(error.retryable());
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("application"),
                Mockito.eq("AUTO_POST_PROVIDER_PROCESSING_FAILED"), Mockito.anyLong());
    }

    private StoryDiscoveryResult discover() {
        return agent.discover(
                Instant.parse("2026-08-22T12:00:00Z"),
                Instant.parse("2026-08-23T12:00:00Z"));
    }

    private static AiConfig aiConfig(String autoPostApiKey) {
        return new AiConfig(
                "openai",
                "low",
                "pepper-key",
                "pepper-model",
                "test-replica",
                autoPostApiKey,
                "autopost-model",
                "top-stories-v3",
                "unwrapped-key",
                "unwrapped-model",
                false);
    }
}
