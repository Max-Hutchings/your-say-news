package com.yoursay.unwrapped;

import java.util.List;

public record UnwrappedResearchResult(
        UnwrappedResearchDraftV1 draft,
        List<String> providerCitations,
        String model,
        String providerResponseId
) {
}
