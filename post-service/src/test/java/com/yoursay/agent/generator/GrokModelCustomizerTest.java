package com.yoursay.agent.generator;

import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrokModelCustomizerTest {

    @Test
    void configuresGrokResponsesModelWithServerSideWebSearch() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);

        new GrokModelCustomizer().customize(builder);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> tools = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).serverTools(tools.capture());
        assertEquals(List.of(Map.of("type", "web_search")), tools.getValue());
    }
}
