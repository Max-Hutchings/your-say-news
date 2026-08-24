package com.yoursay.posts.postagent.dto;

import dev.langchain4j.service.output.JsonSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDraftOutputDescriptionTest {

    @Test
    void everyDraftOutputFieldCarriesABoundedModelRequirement() {
        assertSchemaContains(AgentDraftDto.class, List.of(
                "Return exactly 3 neutral overview claims.",
                "Return exactly 2 strongest material claims supporting the motion.",
                "Return exactly 2 strongest material claims opposing the motion.",
                "One neutral question of at most 20 words asking whether the reader supports the motion.",
                "BINARY for a genuine Agree or Disagree motion; otherwise MULTIPLE_CHOICE.",
                "For BINARY return exactly Agree then Disagree; otherwise return 2 to 5 neutral options of at most 6 words each.",
                "Return 2 to 6 sources referenced by the claims, with no unused or duplicate sources.",
                "One neutral factual image sentence of at most 25 words for a human editor.",
                "A search query of 3 to 8 words for an owned or reusable licensed image.",
                "Exactly one sentence of at most 30 words, distinguishing fact, forecast, allegation or opinion.",
                "Return 1 or 2 exact source URLs that directly support this claim.",
                "A neutral answer label of 1 to 6 words and at most 60 characters.",
                "Exact HTTP or HTTPS source URL with no surrounding commentary.",
                "Exact page or document title of at most 18 words.",
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
