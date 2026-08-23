package com.yoursay.autopost.agent;

import com.yoursay.observability.DomainMetrics;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GrokStoryDiscoveryAgentTest {

    private AutoPostAiClient client;
    private DomainMetrics metrics;
    private GrokStoryDiscoveryAgent agent;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AutoPostAiClient.class);
        metrics = Mockito.mock(DomainMetrics.class);
        agent = new GrokStoryDiscoveryAgent();
        agent.client = client;
        agent.metrics = metrics;
        agent.apiKey = "configured-key";
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
        assertFalse(error.retryable());
        Mockito.verify(metrics).recordOperation(
                Mockito.eq("autopost"), Mockito.eq("providerResearch"), Mockito.eq("fault"),
                Mockito.eq("provider_contract"),
                Mockito.eq("AUTO_POST_PROVIDER_RESPONSE_INVALID"), Mockito.anyLong());
    }
}
