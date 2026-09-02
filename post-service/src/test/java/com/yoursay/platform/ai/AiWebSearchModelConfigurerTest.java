package com.yoursay.platform.ai;

import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiWebSearchModelConfigurerTest {

    @Test
    void configuresOpenAiWebSearchAndCompleteSourceMetadata() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer configurer = configurer("openai", "high");

        configurer.configure(builder);

        assertWebSearchRequired(builder);
        Mockito.verify(builder).include(List.of("web_search_call.action.sources"));
        Mockito.verify(builder).temperature(null);
        Mockito.verify(builder).topP(null);
        Mockito.verify(builder).defaultRequestParameters(DefaultChatRequestParameters.EMPTY);
        Mockito.verify(builder).reasoningEffort("high");
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    void removesSamplingDefaultsThatOpenAiRejectsForGptFivePointSix() {
        HttpClientBuilder httpClientBuilder = Mockito.mock(HttpClientBuilder.class);
        Mockito.when(httpClientBuilder.build()).thenReturn(Mockito.mock(HttpClient.class));
        OpenAiResponsesChatModel.Builder builder = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .apiKey("test-openai-key")
                .modelName("gpt-5.6")
                .temperature(1.0)
                .topP(1.0);

        configurer("openai", "high").configure(builder);

        OpenAiResponsesChatModel model = builder.build();
        assertNull(model.defaultRequestParameters().temperature());
        assertNull(model.defaultRequestParameters().topP());
    }

    @Test
    void configuresGrokWebSearchWithoutInlineCitationsBreakingStructuredOutput() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer configurer = configurer("grok", "low");

        configurer.configure(builder);

        assertWebSearchRequired(builder);
        Mockito.verify(builder).include(List.of("no_inline_citations"));
        Mockito.verifyNoMoreInteractions(builder);
    }

    private static AiWebSearchModelConfigurer configurer(
            String provider,
            String openAiReasoningEffort
    ) {
        AiWebSearchModelConfigurer configurer = new AiWebSearchModelConfigurer();
        configurer.config = new AiConfig(
                provider,
                openAiReasoningEffort,
                "pepper-key",
                "pepper-model",
                "pepper-replica",
                "autopost-key",
                "autopost-model",
                "top-stories-v3",
                "unwrapped-key",
                "unwrapped-model",
                false);
        return configurer;
    }

    private static void assertWebSearchRequired(OpenAiResponsesChatModel.Builder builder) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> tools = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).serverTools(tools.capture());
        assertEquals(List.of(Map.of("type", "web_search")), tools.getValue());
        Mockito.verify(builder).toolChoice(ToolChoice.REQUIRED);
    }
}
