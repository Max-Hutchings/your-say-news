package com.yoursay.platform.ai;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentProviderConfigurationTest {

    private static final Pattern MODEL_NAME =
            Pattern.compile("modelName\\s*=\\s*\"([^\"]+)\"");

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

    private static Set<String> registeredModels() throws Exception {
        Path repositoryRoot = Path.of(System.getProperty("user.dir"));
        Path mainJava = repositoryRoot.resolve("post-service/src/main/java");
        if (!Files.exists(mainJava)) mainJava = repositoryRoot.resolve("src/main/java");

        Set<String> modelNames = new HashSet<>();
        try (var files = Files.walk(mainJava)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("@RegisterAiService")) continue;
                Matcher matcher = MODEL_NAME.matcher(source);
                while (matcher.find()) modelNames.add(matcher.group(1));
            }
        }
        assertEquals(3, modelNames.size(),
                "Every @RegisterAiService model must be intentionally covered here");
        return modelNames;
    }

    private static void assertEveryModelUses(
            SmallRyeConfig config,
            String expectedBaseUrl,
            String expectedApiKey,
            String expectedModel
    ) throws Exception {
        for (String registeredModel : registeredModels()) {
            String prefix = "quarkus.langchain4j.openai." + registeredModel;
            assertEquals(expectedBaseUrl, config.getValue(prefix + ".base-url", String.class));
            assertEquals(expectedApiKey, config.getValue(prefix + ".api-key", String.class));
            assertEquals(expectedModel,
                    config.getValue(prefix + ".chat-model.model-name", String.class));
        }
    }
}
