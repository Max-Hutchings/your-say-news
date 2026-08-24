package com.yoursay.platform.ai;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(AiConfigInjectionTest.OpenAiReasoningProfile.class)
class AiConfigInjectionTest {

    @Inject
    AiConfig config;

    @Test
    void injectsTheConfiguredOpenAiReasoningEffort() {
        assertEquals("high", config.openAi().reasoningEffort());
    }

    @Test
    void applicationConfigurationDefaultsOpenAiReasoningToLow() throws IOException {
        Properties applicationProperties = new Properties();
        Path applicationPropertiesPath = Path.of(System.getProperty("user.dir"),
                "src", "main", "resources", "application.properties");
        try (InputStream input = Files.newInputStream(applicationPropertiesPath)) {
            applicationProperties.load(input);
        }

        assertEquals("${OPENAI_REASONING_EFFORT:low}",
                applicationProperties.getProperty("agent.providers.openai.reasoning-effort"));
    }

    public static final class OpenAiReasoningProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("agent.providers.openai.reasoning-effort", "high");
        }
    }
}
