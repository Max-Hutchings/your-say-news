package com.yoursay.unwrapped.agent;

import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import java.util.List;

public record UnwrappedResearchResult(
        UnwrappedResearchDraftV1 draft,
        List<String> providerCitations,
        String model,
        String providerResponseId
) {
}
