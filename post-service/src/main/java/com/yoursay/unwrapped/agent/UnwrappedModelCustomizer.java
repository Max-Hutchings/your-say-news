package com.yoursay.unwrapped.agent;

import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@ModelName("unwrapped")
public class UnwrappedModelCustomizer
        implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {
    @Override
    public void customize(OpenAiResponsesChatModel.Builder builder) {
        builder.serverTools(List.of(Map.of("type", "web_search")));
        builder.toolChoice(ToolChoice.REQUIRED);
    }
}
