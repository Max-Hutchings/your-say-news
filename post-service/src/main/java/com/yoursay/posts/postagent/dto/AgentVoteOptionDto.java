package com.yoursay.posts.postagent.dto;

import dev.langchain4j.model.output.structured.Description;

public record AgentVoteOptionDto(
        @Description("A neutral answer label of 1 to 6 words and at most 60 characters.")
        String label
) {
}
