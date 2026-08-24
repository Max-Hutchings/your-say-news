package com.yoursay.ai;

import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Applies the provider-specific Responses API settings shared by every research agent. */
@ApplicationScoped
public class AiWebSearchModelConfigurer {

    private static final List<Map<String, Object>> WEB_SEARCH =
            List.of(Map.of("type", "web_search"));

    @ConfigProperty(name = "agent.provider", defaultValue = "openai")
    String provider;

    public void configure(OpenAiResponsesChatModel.Builder builder) {
        List<String> responseIncludes = responseIncludes();
        builder.serverTools(WEB_SEARCH);
        builder.toolChoice(ToolChoice.REQUIRED);
        builder.include(responseIncludes);
    }

    private List<String> responseIncludes() {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openai" -> List.of("web_search_call.action.sources");
            case "grok" -> List.of("no_inline_citations");
            default -> throw new IllegalArgumentException("Unsupported agent.provider: " + provider);
        };
    }
}
