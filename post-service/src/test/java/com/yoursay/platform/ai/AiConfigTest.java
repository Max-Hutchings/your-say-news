package com.yoursay.platform.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConfigTest {

    @Test
    void groupsEveryApplicationLevelAiSettingBehindOneTypedConfiguration() {
        AiConfig config = config("GROK", "pepper-key", "autopost-key", "unwrapped-key");

        assertEquals(AiConfig.Provider.GROK, config.provider());
        assertEquals("pepper-key", config.pepper().apiKey());
        assertEquals("pepper-model", config.pepper().model());
        assertEquals("pepper-replica", config.pepper().replicaId());
        assertEquals("autopost-key", config.autoPost().apiKey());
        assertEquals("autopost-model", config.autoPost().model());
        assertEquals("top-stories-v3", config.autoPost().promptVersion());
        assertEquals("unwrapped-key", config.unwrapped().apiKey());
        assertEquals("unwrapped-model", config.unwrapped().model());
        assertTrue(config.unwrapped().stubbed());
        assertTrue(config.pepper().configured());
        assertTrue(config.autoPost().configured());
        assertTrue(config.unwrapped().configured());
    }

    @Test
    void rejectsUnsupportedProvidersAtTheCentralConfigurationBoundary() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> config("untrusted-provider", "pepper-key", "autopost-key", "unwrapped-key"));

        assertEquals("Unsupported agent.provider: untrusted-provider", error.getMessage());
    }

    @Test
    void treatsEveryMissingApiKeyFormAsUnconfiguredForEveryAgent() {
        for (String missing : new String[]{null, "", "   ", "__not_configured__"}) {
            AiConfig config = config("openai", missing, missing, missing);

            assertFalse(config.pepper().configured());
            assertFalse(config.autoPost().configured());
            assertFalse(config.unwrapped().configured());
        }
    }

    private static AiConfig config(
            String provider,
            String pepperApiKey,
            String autoPostApiKey,
            String unwrappedApiKey
    ) {
        return new AiConfig(
                provider,
                pepperApiKey,
                "pepper-model",
                "pepper-replica",
                autoPostApiKey,
                "autopost-model",
                "top-stories-v3",
                unwrappedApiKey,
                "unwrapped-model",
                true);
    }
}
