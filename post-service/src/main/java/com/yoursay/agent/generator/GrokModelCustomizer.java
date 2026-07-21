package com.yoursay.agent.generator;

import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
@ModelName("grok")
public class GrokModelCustomizer
        implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {

    @Override
    public void customize(OpenAiResponsesChatModel.Builder builder) {
        builder.serverTools(List.of(Map.of("type", "web_search")));
    }
}
