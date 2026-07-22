package com.yoursay.agents.postagent;

import dev.langchain4j.model.output.structured.Description;

public record AgentVoteOptionDto(@Description("Concise neutral answer label") String label) {
}
