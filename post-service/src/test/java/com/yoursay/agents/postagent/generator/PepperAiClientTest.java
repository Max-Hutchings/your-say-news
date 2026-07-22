package com.yoursay.agents.postagent.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.agents.postagent.AgentDraftDto;
import com.yoursay.agents.postagent.AgentSourceDto;
import com.yoursay.agents.postagent.SourcedClaimDto;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PepperAiClientTest {

    private PepperAiService service;
    private PepperAiClient client;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PepperAiService.class);
        client = new PepperAiClient();
        client.service = service;
        client.objectMapper = new ObjectMapper();
    }

    @Test
    void researchReturnsStructuredLangChain4jContentAndRawGrokCitations() {
        AgentDraftDto draft = draft();
        Mockito.when(service.research("Compare the UK voting-age proposal."))
                .thenReturn(result(draft, """
                        {
                          "citations": [
                            "https://www.ons.gov.uk/releases/electiondata",
                            "https://www.parliament.uk/bills/voting-age"
                          ]
                        }
                        """));

        PepperAiResponse response = client.research("  Compare the UK voting-age proposal.  ");

        assertEquals(draft, response.draft());
        assertEquals(List.of(
                "https://www.ons.gov.uk/releases/electiondata",
                "https://www.parliament.uk/bills/voting-age"), response.citations());
        assertEquals("grok-4.3", response.model());
        assertEquals("resp_uk_voting_age", response.providerResponseId());
        Mockito.verify(service).research("Compare the UK voting-age proposal.");
    }

    @Test
    void researchFailsClosedWhenRawResponseCannotProveCitations() {
        Mockito.when(service.research(Mockito.anyString())).thenReturn(Result.<AgentDraftDto>builder()
                .content(draft())
                .finalResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from("structured output"))
                        .metadata(ChatResponseMetadata.builder()
                                .id("resp_without_raw_response")
                                .modelName("grok-4.3")
                                .build())
                        .build())
                .build());

        GenerationException error = assertThrows(GenerationException.class,
                () -> client.research("Compare the UK voting-age proposal."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("LangChain4j returned no raw Grok response for citation verification",
                error.getMessage());
    }

    private static Result<AgentDraftDto> result(AgentDraftDto draft, String rawBody) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(rawBody)
                .build();
        OpenAiResponsesChatResponseMetadata metadata =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("resp_uk_voting_age")
                        .modelName("grok-4.3")
                        .rawHttpResponse(rawResponse)
                        .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("structured output"))
                .metadata(metadata)
                .build();
        return Result.<AgentDraftDto>builder()
                .content(draft)
                .finalResponse(response)
                .build();
    }

    private static AgentDraftDto draft() {
        String statisticsUrl = "https://www.ons.gov.uk/releases/electiondata";
        String parliamentUrl = "https://www.parliament.uk/bills/voting-age";
        return new AgentDraftDto(
                List.of(new SourcedClaimDto("Voting rights would extend to younger people.",
                        List.of(statisticsUrl))),
                List.of(new SourcedClaimDto("Supporters cite representation.",
                        List.of(parliamentUrl))),
                List.of(new SourcedClaimDto("Opponents favour the current threshold.",
                        List.of(statisticsUrl))),
                "Should the UK voting age be lowered to 16?",
                List.of(
                        new AgentSourceDto(statisticsUrl, "Election data", "ONS"),
                        new AgentSourceDto(parliamentUrl, "Voting age bill", "UK Parliament")
                ),
                "A neutral polling station sign.",
                "UK polling station sign licensed image"
        );
    }
}
