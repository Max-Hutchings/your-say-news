package com.yoursay.agents.postagent;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("A factual or attributed claim and the exact web sources supporting it")
public record SourcedClaimDto(
        @Description("The claim text, distinguishing fact, forecast, allegation or opinion")
        String text,
        @Description("One or more exact source URLs found through live web search")
        List<String> sourceUrls
) {
}
