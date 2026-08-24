package com.yoursay.autopost.agent;

import dev.langchain4j.model.output.structured.Description;

@Description("A reporting source for a discovered current story")
public record DiscoveredStorySource(
        @Description("Exact HTTP or HTTPS source URL with no surrounding commentary.") String url,
        @Description("Exact page or report title of at most 18 words.") String title,
        @Description("Publishing organisation name of at most 6 words.") String publisher
) {
}
