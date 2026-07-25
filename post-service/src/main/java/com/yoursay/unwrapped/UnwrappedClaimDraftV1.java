package com.yoursay.unwrapped;

import dev.langchain4j.model.output.structured.Description;
import java.util.List;

public record UnwrappedClaimDraftV1(
        @Description("Stable claim id") String id,
        @Description("Concise externally supported factual statement") String statement,
        @Description("One or more source ids supporting the statement") List<String> sourceIds,
        @Description("True only when this is cautious interpretation rather than sourced fact")
        boolean interpretation
) {
}
