package com.yoursay.autopost.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the separate editorial and structured-output instructions for auto-post discovery. */
public final class AutoPostSystemPrompt {
    public static final String DEFAULT = load("/prompts/autopost/system-prompt.md");
    public static final String OUTPUT_INSTRUCTIONS =
            load("/prompts/autopost/output-instructions.md");

    private static String load(String resource) {
        try (InputStream input = AutoPostSystemPrompt.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing auto-post prompt resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read auto-post prompt resource: " + resource,
                    failure);
        }
    }

    private AutoPostSystemPrompt() {
    }
}
