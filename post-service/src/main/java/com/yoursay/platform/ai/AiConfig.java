package com.yoursay.platform.ai;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Locale;

/** Central application-level configuration for every AI-backed capability. */
@Singleton
public class AiConfig {

    private static final String NOT_CONFIGURED = "__not_configured__";

    private final Provider provider;
    private final Pepper pepper;
    private final AutoPost autoPost;
    private final Unwrapped unwrapped;

    @Inject
    public AiConfig(
            @ConfigProperty(name = "agent.provider", defaultValue = "openai") String provider,
            @ConfigProperty(name = "pepper.agent.api-key", defaultValue = NOT_CONFIGURED)
            String pepperApiKey,
            @ConfigProperty(name = "pepper.agent.model", defaultValue = "configured-model")
            String pepperModel,
            @ConfigProperty(name = "pepper.replica-id", defaultValue = "local")
            String pepperReplicaId,
            @ConfigProperty(name = "autopost.agent.api-key", defaultValue = NOT_CONFIGURED)
            String autoPostApiKey,
            @ConfigProperty(name = "autopost.agent.model", defaultValue = "configured-model")
            String autoPostModel,
            @ConfigProperty(name = "autopost.agent.prompt-version", defaultValue = "top-stories-v2")
            String autoPostPromptVersion,
            @ConfigProperty(name = "unwrapped.agent.api-key", defaultValue = NOT_CONFIGURED)
            String unwrappedApiKey,
            @ConfigProperty(name = "unwrapped.agent.model", defaultValue = "configured-model")
            String unwrappedModel,
            @ConfigProperty(name = "unwrapped.agent.stubbed", defaultValue = "false")
            boolean unwrappedStubbed
    ) {
        this.provider = Provider.from(provider);
        this.pepper = new Pepper(pepperApiKey, pepperModel, pepperReplicaId);
        this.autoPost = new AutoPost(autoPostApiKey, autoPostModel, autoPostPromptVersion);
        this.unwrapped = new Unwrapped(unwrappedApiKey, unwrappedModel, unwrappedStubbed);
    }

    public Provider provider() {
        return provider;
    }

    public Pepper pepper() {
        return pepper;
    }

    public AutoPost autoPost() {
        return autoPost;
    }

    public Unwrapped unwrapped() {
        return unwrapped;
    }

    public enum Provider {
        OPENAI,
        GROK;

        private static Provider from(String value) {
            if (value != null) {
                try {
                    return valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // Report the application property below with one stable configuration error.
                }
            }
            throw new IllegalArgumentException("Unsupported agent.provider: " + value);
        }
    }

    public static final class Pepper {
        private final String apiKey;
        private final String model;
        private final String replicaId;

        private Pepper(String apiKey, String model, String replicaId) {
            this.apiKey = apiKey;
            this.model = model;
            this.replicaId = replicaId;
        }

        public String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public String replicaId() {
            return replicaId;
        }

        public boolean configured() {
            return configuredApiKey(apiKey);
        }
    }

    public static final class AutoPost {
        private final String apiKey;
        private final String model;
        private final String promptVersion;

        private AutoPost(String apiKey, String model, String promptVersion) {
            this.apiKey = apiKey;
            this.model = model;
            this.promptVersion = promptVersion;
        }

        public String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public String promptVersion() {
            return promptVersion;
        }

        public boolean configured() {
            return configuredApiKey(apiKey);
        }
    }

    public static final class Unwrapped {
        private final String apiKey;
        private final String model;
        private final boolean stubbed;

        private Unwrapped(String apiKey, String model, boolean stubbed) {
            this.apiKey = apiKey;
            this.model = model;
            this.stubbed = stubbed;
        }

        public String apiKey() {
            return apiKey;
        }

        public String model() {
            return model;
        }

        public boolean stubbed() {
            return stubbed;
        }

        public boolean configured() {
            return configuredApiKey(apiKey);
        }
    }

    private static boolean configuredApiKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }
}
