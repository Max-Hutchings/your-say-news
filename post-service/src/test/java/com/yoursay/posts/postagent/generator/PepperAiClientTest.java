package com.yoursay.posts.postagent.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.postagent.dto.AgentDraftDto;
import com.yoursay.posts.postagent.dto.AgentSourceDto;
import com.yoursay.posts.postagent.dto.SourcedClaimDto;
import com.yoursay.posts.postagent.generator.GenerationException;
import com.yoursay.posts.postagent.generator.PepperAiClient;
import com.yoursay.posts.postagent.generator.PepperAiResponse;
import com.yoursay.posts.postagent.generator.PepperAiService;
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
        Mockito.when(service.research(PepperSystemPrompt.DEFAULT,
                        PepperSystemPrompt.OUTPUT_INSTRUCTIONS,
                        "Compare the UK voting-age proposal."))
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
        Mockito.verify(service).research(PepperSystemPrompt.DEFAULT,
                PepperSystemPrompt.OUTPUT_INSTRUCTIONS,
                "Compare the UK voting-age proposal.");
    }

    @Test
    void researchReturnsOpenAiAnnotationAndSearchActionSourcesWithoutDuplicates() {
        AgentDraftDto draft = draft();
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft, """
                        {
                          "output": [
                            {
                              "type": "web_search_call",
                              "status": "completed",
                              "action": {
                                "sources": [
                                  {"url": "https://www.ons.gov.uk/releases/electiondata"},
                                  {"url": "https://www.ons.gov.uk/releases/electiondata"}
                                ]
                              }
                            },
                            {
                              "type": "message",
                              "content": [{
                                "annotations": [{
                                  "type": "url_citation",
                                  "url": "https://www.parliament.uk/bills/voting-age"
                                }]
                              }]
                            }
                          ]
                        }
                        """));

        PepperAiResponse response = client.research("Compare the UK voting-age proposal.");

        assertEquals(List.of(
                "https://www.parliament.uk/bills/voting-age",
                "https://www.ons.gov.uk/releases/electiondata"), response.citations());
    }

    @Test
    void researchIgnoresCitationShapedDataOutsideWebSearchAndMessageOutput() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), """
                        {
                          "output": [{
                            "type": "reasoning",
                            "sources": [{"url": "https://decoy.example/source"}],
                            "annotations": [{
                              "type": "url_citation",
                              "url": "https://decoy.example/annotation"
                            }]
                          }]
                        }
                        """));

        PepperAiResponse response = client.research("Compare the UK voting-age proposal.");

        assertEquals(List.of(), response.citations());
    }

    @Test
    void researchIgnoresSourcesFromAFailedOpenAiWebSearchCall() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), """
                        {"output":[{
                          "type":"web_search_call",
                          "status":"failed",
                          "action":{"sources":[{
                            "url":"https://fabricated.example/failed-search"
                          }]}
                        }]}
                        """));

        PepperAiResponse response = client.research("Compare the UK voting-age proposal.");

        assertEquals(List.of(), response.citations());
    }

    @Test
    void researchPreservesHttpProviderCitationsAcceptedByTheDraftValidator() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), """
                        {"citations":["http://public-record.example/archive"]}
                        """));

        PepperAiResponse response = client.research("Compare the UK voting-age proposal.");

        assertEquals(List.of("http://public-record.example/archive"), response.citations());
    }

    @Test
    void researchRejectsNonHttpValuesFromRecognisedCitationLocations() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), """
                        {
                          "citations": [
                            "https://www.ons.gov.uk/releases/electiondata",
                            "file:///etc/passwd",
                            "javascript:alert(1)",
                            42
                          ],
                          "output": [{
                            "type": "web_search_call",
                            "status": "completed",
                            "action": {"sources": [
                              {"url": "data:text/plain,not-a-source"}
                            ]}
                          }]
                        }
                        """));

        PepperAiResponse response = client.research("Compare the UK voting-age proposal.");

        assertEquals(List.of("https://www.ons.gov.uk/releases/electiondata"),
                response.citations());
    }

    @Test
    void researchFailsClosedWhenRawResponseCannotProveCitations() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Result.<AgentDraftDto>builder()
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
        assertEquals("LangChain4j returned no raw provider response for citation verification",
                error.getMessage());
    }

    @Test
    void researchFailsClosedWhenOpenAiMetadataHasNoRawResponse() {
        OpenAiResponsesChatResponseMetadata metadata =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("resp_without_raw_response")
                        .modelName("gpt-5.6")
                        .build();
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), metadata));

        GenerationException error = assertThrows(GenerationException.class,
                () -> client.research("Compare the UK voting-age proposal."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("LangChain4j returned no raw provider response for citation verification",
                error.getMessage());
    }

    @Test
    void researchFailsClosedWhenOpenAiRawResponseHasNoBody() {
        OpenAiResponsesChatResponseMetadata metadata =
                Mockito.mock(OpenAiResponsesChatResponseMetadata.class);
        SuccessfulHttpResponse rawResponse = Mockito.mock(SuccessfulHttpResponse.class);
        Mockito.when(metadata.rawHttpResponse()).thenReturn(rawResponse);
        Mockito.when(rawResponse.body()).thenReturn(null);
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), metadata));

        GenerationException error = assertThrows(GenerationException.class,
                () -> client.research("Compare the UK voting-age proposal."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("LangChain4j returned no raw provider response for citation verification",
                error.getMessage());
    }

    @Test
    void researchMapsMalformedProviderJsonToASafeStableFailure() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(result(draft(), "{not-json"));

        GenerationException error = assertThrows(GenerationException.class,
                () -> client.research("Compare the UK voting-age proposal."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("Could not read provider citations from the LangChain4j response",
                error.getMessage());
    }

    @Test
    void researchRejectsAMissingFinalResponseBeforeReadingProviderEvidence() {
        Mockito.when(service.research(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Result.<AgentDraftDto>builder().content(draft()).build());

        GenerationException error = assertThrows(GenerationException.class,
                () -> client.research("Compare the UK voting-age proposal."));

        assertEquals("AGENT_PROVIDER_RESPONSE_INVALID", error.code());
        assertFalse(error.retryable());
        assertEquals("LangChain4j returned no final provider response", error.getMessage());
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
        return result(draft, metadata);
    }

    private static Result<AgentDraftDto> result(
            AgentDraftDto draft,
            ChatResponseMetadata metadata
    ) {
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
