package com.yoursay.autopost.agent;

import com.yoursay.autopost.AutoPostRegion;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("One ranked current news story returned by the research provider")
public record DiscoveredStoryDraft(
        @Description("Unique rank from 1, most important, through 10") int rank,
        @Description("One primary region: UK, US or GLOBAL") AutoPostRegion region,
        @Description("Neutral concise headline") String headline,
        @Description("Neutral factual explanation of the material development") String summary,
        @Description("Lowercase stable key describing the underlying event") String deduplicationKey,
        @Description("ISO-8601 UTC timestamp, for example 2026-08-23T12:34:56Z") String publishedAt,
        @Description("One or more independent or primary reporting sources")
        List<DiscoveredStorySource> sources
) {
}
