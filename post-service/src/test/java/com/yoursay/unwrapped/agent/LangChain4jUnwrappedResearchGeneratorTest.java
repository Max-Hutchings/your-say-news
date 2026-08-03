package com.yoursay.unwrapped.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.validation.UnwrappedDraftValidator;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChain4jUnwrappedResearchGeneratorTest {
    @Test
    void developmentStubReturnsFixedDraftWithoutCallingTheProvider() {
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        OptionBriefV1 option = mock(OptionBriefV1.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        when(option.option()).thenReturn(new VoteOptionDto(15L, "Agree", 0, "AGREE"));
        when(option.overallVoteCount()).thenReturn(377L);
        when(option.overallVotePercentage()).thenReturn(62.83);
        when(option.candidates()).thenReturn(List.of());
        when(request.postId()).thenReturn(2005L);
        when(request.options()).thenReturn(List.of(option));

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.stubbed = true;

        UnwrappedResearchResult result = generator.generate(request);

        assertEquals("stubbed-unwrapped", result.model());
        assertEquals("stub-post-2005", result.providerResponseId());
        assertEquals(List.of(15L), result.draft().pages().stream()
                .map(page -> page.optionId()).toList());
        assertEquals("Development preview for Agree", result.draft().pages().getFirst().headline());
        assertEquals(List.of(), result.draft().sources());
        verifyNoInteractions(aiService);
    }

    @Test
    void combinesStructuredDraftWithCapturedRawResponseMetadata() {
        UnwrappedResearchDraftV1 draft = mock(UnwrappedResearchDraftV1.class);
        UnwrappedResearchRequest request = mock(UnwrappedResearchRequest.class);
        UnwrappedResearchAiService aiService = mock(UnwrappedResearchAiService.class);
        UnwrappedDraftValidator validator = mock(UnwrappedDraftValidator.class);
        UnwrappedChatResponseCapture capture = new UnwrappedChatResponseCapture();
        ChatResponse response = responseWithCitation("https://www.ons.gov.uk/source");
        when(aiService.research(anyString())).thenAnswer(ignored -> {
            capture.onResponse(new ChatModelResponseContext(response, mock(ChatRequest.class),
                    ModelProvider.OPEN_AI, new HashMap<>()));
            return draft;
        });

        LangChain4jUnwrappedResearchGenerator generator =
                new LangChain4jUnwrappedResearchGenerator();
        generator.aiService = aiService;
        generator.responseCapture = capture;
        generator.objectMapper = new ObjectMapper();
        generator.validator = validator;
        generator.apiKey = "test-key";
        generator.configuredModel = "grok-test";

        UnwrappedResearchResult result = generator.generate(request);

        assertSame(draft, result.draft());
        assertEquals(List.of("https://www.ons.gov.uk/source"), result.providerCitations());
        assertEquals("grok-test", result.model());
        assertEquals("response-42", result.providerResponseId());
        verify(validator).validate(request, draft, List.of("https://www.ons.gov.uk/source"));
    }

    private static ChatResponse responseWithCitation(String citation) {
        SuccessfulHttpResponse rawResponse = SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body("""
                        {"output":[{"content":[{"annotations":[
                          {"type":"url_citation","url":"%s"}
                        ]}]}]}
                        """.formatted(citation))
                .build();
        OpenAiResponsesChatResponseMetadata metadata =
                OpenAiResponsesChatResponseMetadata.builder()
                        .id("response-42")
                        .modelName("grok-test")
                        .rawHttpResponse(rawResponse)
                        .build();
        return ChatResponse.builder()
                .aiMessage(AiMessage.from("{}"))
                .metadata(metadata)
                .build();
    }
}
