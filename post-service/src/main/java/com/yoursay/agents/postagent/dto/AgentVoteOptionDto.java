package com.yoursay.agents.postagent.dto;

import dev.langchain4j.model.output.structured.Description;

public record AgentVoteOptionDto(@Description("Concise neutral answer label") String label) {
}
