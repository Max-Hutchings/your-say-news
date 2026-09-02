package com.yoursay.posts.postagent.dto;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Description("A web source used by one or more draft claims")
public record AgentSourceDto(
        @Description("Exact HTTP or HTTPS source URL with no surrounding commentary.")
        @NotBlank @Size(max = 2048) String url,
        @Description("Exact page or document title of at most 18 words.")
        @NotBlank @Size(max = 512) String title,
        @Description("Publishing organisation name of at most 6 words.")
        @NotBlank @Size(max = 256) String publisher
) {
}
