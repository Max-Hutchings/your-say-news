package com.yoursay.posts.postagent.agent;

import com.yoursay.platform.ai.AiWebSearchModelConfigurer;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import io.quarkiverse.langchain4j.ModelBuilderCustomizer;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@ModelName("pepper")
public class PepperModelCustomizer
        implements ModelBuilderCustomizer<OpenAiResponsesChatModel.Builder> {

    @Inject
    AiWebSearchModelConfigurer webSearch;

    @Override
    public void customize(OpenAiResponsesChatModel.Builder builder) {
        webSearch.configure(builder);
        builder.textVerbosity("low");
    }
}
