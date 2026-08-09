package com.yoursay.unwrapped.dto;

import com.yoursay.unwrapped.SourceClassification;
import dev.langchain4j.model.output.structured.Description;

public record UnwrappedSourceDraftV1(
        @Description("Stable source id used by claims") String id,
        @Description("Exact HTTPS source URL found during research") String url,
        @Description("Publisher or institution") String publisher,
        @Description("Source title") String title,
        @Description("OFFICIAL, ACADEMIC, REPUTABLE_MEDIA, or OTHER") SourceClassification classification
) {
}
