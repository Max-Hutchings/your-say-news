package com.yoursay.ai;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentProviderConfigurationTest {

    private static final String[] REGISTERED_MODELS = {"pepper", "autopost", "unwrapped"};

    @Test
    void openAiSelectionConfiguresEveryRegisteredAgent() throws Exception {
        SmallRyeConfig config = configFor("openai", "openai-test-key");

        assertEquals("openai", config.getValue("agent.provider", String.class));
        assertEquals("responses",
                config.getValue("quarkus.langchain4j.openai.chat-model.mode", String.class));
        assertEveryModelUses(config, "https://api.openai.com/v1", "openai-test-key", "gpt-5.6");
    }

    @Test
    void grokSelectionConfiguresEveryRegisteredAgent() throws Exception {
        SmallRyeConfig config = configFor("grok", "grok-test-key");

        assertEquals("grok", config.getValue("agent.provider", String.class));
        assertEquals("responses",
                config.getValue("quarkus.langchain4j.openai.chat-model.mode", String.class));
        assertEveryModelUses(config, "https://api.x.ai/v1", "grok-test-key", "grok-4.5");
    }

    private static SmallRyeConfig configFor(String provider, String apiKey) throws Exception {
        URL applicationProperties = mainApplicationProperties().toUri().toURL();
        PropertiesConfigSource overrides = new PropertiesConfigSource(Map.of(
                "agent.provider", provider,
                "agent.providers." + provider + ".api-key", apiKey
        ), "test-overrides", 500);

        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withSources(new PropertiesConfigSource(applicationProperties), overrides)
                .build();
    }

    private static Path mainApplicationProperties() {
        Path repositoryRoot = Path.of(System.getProperty("user.dir"));
        Path fromRepository = repositoryRoot.resolve(
                "post-service/src/main/resources/application.properties");
        if (Files.exists(fromRepository)) return fromRepository;
        return repositoryRoot.resolve("src/main/resources/application.properties");
    }

    private static void assertEveryModelUses(
            SmallRyeConfig config,
            String expectedBaseUrl,
            String expectedApiKey,
            String expectedModel
    ) {
        for (String registeredModel : REGISTERED_MODELS) {
            String prefix = "quarkus.langchain4j.openai." + registeredModel;
            assertEquals(expectedBaseUrl, config.getValue(prefix + ".base-url", String.class));
            assertEquals(expectedApiKey, config.getValue(prefix + ".api-key", String.class));
            assertEquals(expectedModel,
                    config.getValue(prefix + ".chat-model.model-name", String.class));
        }
    }
}
