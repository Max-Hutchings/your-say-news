package com.yoursay.autopost.agent;

import com.yoursay.autopost.AutoPostRegion;
import dev.langchain4j.model.output.structured.Description;

import java.time.Instant;
import java.util.List;

@Description("One ranked current news story")
public record DiscoveredStory(
        @Description("Unique rank from 1, most important, through 10") int rank,
        @Description("One primary region: UK, US or GLOBAL") AutoPostRegion region,
        @Description("Neutral concise headline") String headline,
        @Description("Neutral factual explanation of the material development") String summary,
        @Description("Lowercase stable key describing the underlying event, shared by duplicate coverage")
        String deduplicationKey,
        @Description("When the material development was first reported or officially announced")
        Instant publishedAt,
        @Description("One or more independent or primary reporting sources")
        List<DiscoveredStorySource> sources
) {
}
