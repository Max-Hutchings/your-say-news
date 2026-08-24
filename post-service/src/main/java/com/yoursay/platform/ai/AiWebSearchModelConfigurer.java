package com.yoursay.platform.ai;

import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/** Applies the provider-specific Responses API settings shared by every research agent. */
@ApplicationScoped
public class AiWebSearchModelConfigurer {

    private static final List<Map<String, Object>> WEB_SEARCH =
            List.of(Map.of("type", "web_search"));

    @Inject
    AiConfig config;

    public void configure(OpenAiResponsesChatModel.Builder builder) {
        removeUnsupportedOpenAiSamplingParameters(builder);
        List<String> responseIncludes = responseIncludes();
        builder.serverTools(WEB_SEARCH);
        builder.toolChoice(ToolChoice.REQUIRED);
        builder.include(responseIncludes);
    }

    private void removeUnsupportedOpenAiSamplingParameters(
            OpenAiResponsesChatModel.Builder builder
    ) {
        if (config.provider() != AiConfig.Provider.OPENAI) return;
        builder.temperature(null);
        builder.topP(null);
        builder.defaultRequestParameters(DefaultChatRequestParameters.EMPTY);
        builder.reasoningEffort(config.openAi().reasoningEffort());
    }

    private List<String> responseIncludes() {
        return switch (config.provider()) {
            case OPENAI -> List.of("web_search_call.action.sources");
            case GROK -> List.of("no_inline_citations");
        };
    }
}
