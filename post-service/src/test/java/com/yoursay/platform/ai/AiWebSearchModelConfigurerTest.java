package com.yoursay.platform.ai;

import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiWebSearchModelConfigurerTest {

    @Test
    void configuresOpenAiWebSearchAndCompleteSourceMetadata() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer configurer = configurer("openai");

        configurer.configure(builder);

        assertWebSearchRequired(builder);
        Mockito.verify(builder).include(List.of("web_search_call.action.sources"));
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    void configuresGrokWebSearchWithoutInlineCitationsBreakingStructuredOutput() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);
        AiWebSearchModelConfigurer configurer = configurer("grok");

        configurer.configure(builder);

        assertWebSearchRequired(builder);
        Mockito.verify(builder).include(List.of("no_inline_citations"));
        Mockito.verifyNoMoreInteractions(builder);
    }

    @Test
    void rejectsAnUnsupportedProviderBeforeCallingTheBuilder() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> configurer("untrusted-provider").configure(builder));

        assertEquals("Unsupported agent.provider: untrusted-provider", error.getMessage());
        Mockito.verifyNoInteractions(builder);
    }

    private static AiWebSearchModelConfigurer configurer(String provider) {
        AiWebSearchModelConfigurer configurer = new AiWebSearchModelConfigurer();
        configurer.config = new AiConfig(
                provider,
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
