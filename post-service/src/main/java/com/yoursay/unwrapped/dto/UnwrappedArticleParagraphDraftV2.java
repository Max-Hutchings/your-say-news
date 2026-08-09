package com.yoursay.unwrapped.dto;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record UnwrappedArticleParagraphDraftV2(
        @Description("One paragraph of the unified persuasive analysis") String text,
        @Description("One or more source ids supporting the researched context in this paragraph")
        List<String> sourceIds
) {
}
