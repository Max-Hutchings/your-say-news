package com.yoursay.autopost.agent;

import com.yoursay.autopost.AutoPostRegion;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("One ranked current news story returned by the research provider")
public record DiscoveredStoryDraft(
        @Description("Integer from 1 to 10; each rank must appear exactly once and 1 is most important.")
        int rank,
        @Description("Exactly one of UK, US or GLOBAL; use the story's primary audience or impact.")
        AutoPostRegion region,
        @Description("One neutral sentence fragment of at most 14 words; no clickbait or trailing full stop.")
        String headline,
        @Description("One neutral factual sentence of at most 35 words describing the material new development.")
        String summary,
        @Description("Three to eight lowercase words joined by hyphens that identify the event, not the publisher.")
        String deduplicationKey,
        @Description("ISO-8601 UTC timestamp ending in Z for when the development was reported, for example 2026-08-23T12:34:56Z.")
        String publishedAt,
        @Description("Return 1 or 2 independent or primary reporting sources for this story; no duplicates.")
        List<DiscoveredStorySource> sources
) {
}
