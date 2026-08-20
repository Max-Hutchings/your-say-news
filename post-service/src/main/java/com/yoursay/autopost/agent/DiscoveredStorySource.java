package com.yoursay.autopost.agent;

import dev.langchain4j.model.output.structured.Description;

@Description("A reporting source for a discovered current story")
public record DiscoveredStorySource(
        @Description("Exact HTTP or HTTPS source URL") String url,
        @Description("Page or report title") String title,
        @Description("Publishing organisation") String publisher
) {
}
