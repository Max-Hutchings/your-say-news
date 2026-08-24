package com.yoursay.posts.postagent.dto;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("A factual or attributed claim and the exact web sources supporting it")
public record SourcedClaimDto(
        @Description("Exactly one sentence of at most 30 words, distinguishing fact, forecast, allegation or opinion.")
        String text,
        @Description("Return 1 or 2 exact source URLs that directly support this claim.")
        List<String> sourceUrls
) {
}
