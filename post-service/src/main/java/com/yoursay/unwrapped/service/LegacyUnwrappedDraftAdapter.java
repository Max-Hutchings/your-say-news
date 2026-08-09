package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class LegacyUnwrappedDraftAdapter {
    private LegacyUnwrappedDraftAdapter() {
    }

    static UnwrappedResearchDraftV1 convert(JsonNode json, ObjectMapper objectMapper) {
        List<UnwrappedSourceDraftV1> sources = new ArrayList<>();
        json.path("sources").forEach(source ->
                sources.add(objectMapper.convertValue(source, UnwrappedSourceDraftV1.class)));
        List<UnwrappedArgumentDraftV1> pages = new ArrayList<>();
        json.path("pages").forEach(page -> pages.add(convertPage(page)));
        return new UnwrappedResearchDraftV1(List.copyOf(pages), List.copyOf(sources));
    }

    private static UnwrappedArgumentDraftV1 convertPage(JsonNode page) {
        List<String> cohorts = strings(page.path("usedCohortIds"));
        List<UnwrappedArticleParagraphDraftV2> paragraphs = new ArrayList<>();
        List<JsonNode> claims = new ArrayList<>();
        page.path("contextClaims").forEach(claims::add);
        for (int index = 0; index < Math.min(2, claims.size()); index++) {
            JsonNode claim = claims.get(index);
            String text = claim.path("statement").asText();
            if (index == 1 && claims.size() > 2) {
                text += " " + claims.subList(2, claims.size()).stream()
                        .map(value -> value.path("statement").asText())
                        .reduce((left, right) -> left + " " + right).orElse("");
            }
            paragraphs.add(new UnwrappedArticleParagraphDraftV2(
                    text, sourceIds(index == 1 ? claims.subList(1, claims.size()) : List.of(claim))));
        }
        JsonNode synthesis = page.path("synthesis");
        if (synthesis.isTextual() && !synthesis.asText().isBlank()) {
            paragraphs.add(new UnwrappedArticleParagraphDraftV2(synthesis.asText(),
                    sourceIds(claims)));
        }
        return new UnwrappedArgumentDraftV1(
                page.path("optionId").longValue(), page.path("headline").asText(),
                cohorts, List.copyOf(paragraphs), page.path("caveat").asText());
    }

    private static List<String> sourceIds(List<JsonNode> claims) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        claims.forEach(claim -> claim.path("sourceIds").forEach(id -> ids.add(id.asText())));
        return List.copyOf(ids);
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }
}
