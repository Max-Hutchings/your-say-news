package com.yoursay.posts.postagent.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads Pepper's separate editorial and structured-output instructions. */
public final class PepperSystemPrompt {
    public static final String DEFAULT = load("/prompts/postagent/system-prompt.md");
    public static final String OUTPUT_INSTRUCTIONS =
            load("/prompts/postagent/output-instructions.md");

    private static String load(String resource) {
        try (InputStream input = PepperSystemPrompt.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing post-agent prompt resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read post-agent prompt resource: " + resource,
                    failure);
        }
    }

    private PepperSystemPrompt() {
    }
}
