package com.yoursay.agent;

import dev.langchain4j.model.output.structured.Description;

@Description("A web source used by one or more draft claims")
public record AgentSourceDto(
        @Description("Exact HTTP or HTTPS source URL")
        String url,
        @Description("Page or document title")
        String title,
        @Description("Publishing organisation")
        String publisher
) {
}
