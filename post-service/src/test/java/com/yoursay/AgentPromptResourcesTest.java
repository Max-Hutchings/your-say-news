package com.yoursay;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptResourcesTest {

    @ParameterizedTest
    @MethodSource("promptResources")
    void everyAgentPromptIsASeparateNonBlankMarkdownResource(
            String resource,
            List<String> requiredInstructions
    )
            throws IOException {
        try (InputStream input = AgentPromptResourcesTest.class.getResourceAsStream(resource)) {
            assertTrue(input != null, () -> "Missing prompt resource " + resource);
            String prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(!prompt.isBlank(), () -> "Blank prompt resource " + resource);
            requiredInstructions.forEach(instruction -> assertTrue(prompt.contains(instruction),
                    () -> resource + " is missing required instruction: " + instruction));
        }
    }

    private static Stream<Arguments> promptResources() {
        return Stream.of(
                Arguments.of("/prompts/autopost/system-prompt.md", List.of(
                        "current-story discovery editor", "live web search", "UK", "US", "GLOBAL",
                        "duplicates", "neutral and factual", "supplied window")),
                Arguments.of("/prompts/autopost/output-instructions.md", List.of(
                        "exactly ten", "Rank", "`headline`", "at least one source", "web search")),
                Arguments.of("/prompts/postagent/system-prompt.md", List.of(
                        "You are Pepper", "live web search", "neutral factual", "case for",
                        "case against", "Only cite URLs")),
                Arguments.of("/prompts/postagent/output-instructions.md", List.of(
                        "exactly three `summaryClaims`", "exactly two `caseForClaims`",
                        "exactly two `caseAgainstClaims`", "at most 30 words",
                        "one or two exact source URLs", "two to six sources",
                        "support question", "BINARY", "MULTIPLE_CHOICE", "web search")),
                Arguments.of("/prompts/unwrapped/system-prompt.md", List.of(
                        "Post Unwrapped", "privacy-safe cohort", "aggregate voting pattern",
                        "British English")),
                Arguments.of("/prompts/unwrapped/output-instructions.md", List.of(
                        "Return exactly", "50 to 100 words", "web search", "`sourceIds`",
                        "Do not identify individual voters"))
        );
    }
}
