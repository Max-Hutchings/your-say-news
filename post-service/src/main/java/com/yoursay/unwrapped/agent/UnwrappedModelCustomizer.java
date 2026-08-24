package com.yoursay.unwrapped.agent;

import com.yoursay.ai.AiWebSearchModelConfigurer;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@ModelName("unwrapped")
public class UnwrappedModelCustomizer
        implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {

    @Inject
    AiWebSearchModelConfigurer webSearch;

    @Override
    public void customize(OpenAiResponsesChatModel.Builder builder) {
        webSearch.configure(builder);
    }
}
