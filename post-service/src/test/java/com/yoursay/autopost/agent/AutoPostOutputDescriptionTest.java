package com.yoursay.autopost.agent;

import dev.langchain4j.service.output.JsonSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPostOutputDescriptionTest {

    @Test
    void everyDiscoveryOutputFieldCarriesABoundedModelRequirement() {
        assertSchemaContains(StoryDiscoveryDraft.class, List.of(
                "Return exactly 10 stories, ordered by rank, with no duplicate underlying events.",
                "Integer from 1 to 10; each rank must appear exactly once and 1 is most important.",
                "Exactly one of UK, US or GLOBAL; use the story's primary audience or impact.",
                "One neutral sentence fragment of at most 14 words; no clickbait or trailing full stop.",
                "One neutral factual sentence of at most 35 words describing the material new development.",
                "Three to eight lowercase words joined by hyphens that identify the event, not the publisher.",
                "ISO-8601 UTC timestamp ending in Z for when the development was reported, for example 2026-08-23T12:34:56Z.",
                "Return 1 or 2 independent or primary reporting sources for this story; no duplicates.",
                "Exact HTTP or HTTPS source URL with no surrounding commentary.",
                "Exact page or report title of at most 18 words.",
                "Publishing organisation name of at most 6 words."
        ));
    }

    private static void assertSchemaContains(Class<?> outputType, List<String> requirements) {
        String schema = JsonSchemas.jsonSchemaFrom(outputType).orElseThrow().toString();
        requirements.forEach(requirement -> assertTrue(
                schema.contains(requirement),
                () -> "Generated schema omitted requirement: " + requirement));
    }
}
