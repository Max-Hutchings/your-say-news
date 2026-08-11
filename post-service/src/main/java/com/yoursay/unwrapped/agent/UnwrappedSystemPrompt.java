package com.yoursay.unwrapped.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Loads and prepares the Markdown instructions supplied to the Post Unwrapped AI service. */
public final class UnwrappedSystemPrompt {
    private static final String SYSTEM_PROMPT_RESOURCE = "/prompts/unwrapped/system-prompt.md";
    private static final String OUTPUT_INSTRUCTIONS_RESOURCE =
            "/prompts/unwrapped/output-instructions.md";

    public static final String DEFAULT = load(SYSTEM_PROMPT_RESOURCE);
    private static final String OUTPUT_INSTRUCTIONS = load(OUTPUT_INSTRUCTIONS_RESOURCE);

    public static String outputInstructions(List<Long> optionIds) {
        return OUTPUT_INSTRUCTIONS
                .replace("{{pageCount}}", Integer.toString(optionIds.size()))
                .replace("{{optionIds}}", optionIds.toString());
    }

    private static String load(String resource) {
        try (InputStream input = UnwrappedSystemPrompt.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing Unwrapped prompt resource: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read Unwrapped prompt resource: " + resource,
                    failure);
        }
    }

    private UnwrappedSystemPrompt() {
    }
}
