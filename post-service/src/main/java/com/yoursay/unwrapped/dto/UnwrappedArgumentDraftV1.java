package com.yoursay.unwrapped.dto;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record UnwrappedArgumentDraftV1(
        @Description("Existing option id copied exactly from the request") Long optionId,
        @Description("Catchy 6 to 10 word headline; name a selected cohort when one is supplied") String headline,
        @Description("One or two supplied cohort ids, or an empty list when no candidate is supplied")
        List<String> selectedCohortIds,
        @Description("Two or three paragraphs totalling 50 to 100 words")
        List<UnwrappedArticleParagraphDraftV2> paragraphs,
        @Description("Short sample limitation kept outside the article body") String caveat
) {
}
