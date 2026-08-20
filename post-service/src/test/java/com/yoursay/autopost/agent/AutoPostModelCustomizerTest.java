package com.yoursay.autopost.agent;

import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoPostModelCustomizerTest {

    @Test
    void requiresServerSideWebSearchForTheNamedAutoPostModel() {
        OpenAiResponsesChatModel.Builder builder =
                Mockito.mock(OpenAiResponsesChatModel.Builder.class);

        new AutoPostModelCustomizer().customize(builder);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> tools = ArgumentCaptor.forClass(List.class);
        Mockito.verify(builder).serverTools(tools.capture());
        assertEquals(List.of(Map.of("type", "web_search")), tools.getValue());
        Mockito.verify(builder).toolChoice(ToolChoice.REQUIRED);
    }
}
