package com.yoursay.unwrapped.dto;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record UnwrappedResearchDraftV1(
        @Description("Required and non-empty. Return exactly one page for every supplied option, "
                + "in supplied order. The number of pages must equal the number of supplied options")
        List<UnwrappedArgumentDraftV1> pages,
        @Description("Every source referenced by any paragraph, with no more than 20 sources in total")
        List<UnwrappedSourceDraftV1> sources
) {
}
