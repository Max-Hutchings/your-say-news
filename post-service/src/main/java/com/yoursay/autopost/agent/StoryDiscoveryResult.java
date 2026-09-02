package com.yoursay.autopost.agent;

import java.util.List;

public record StoryDiscoveryResult(
        List<DiscoveredStory> stories,
        String model,
        String providerResponseId,
        List<String> providerCitations,
        String rawProviderResponse
) {
    public StoryDiscoveryResult(
            List<DiscoveredStory> stories,
            String model,
            String providerResponseId,
            List<String> providerCitations
    ) {
        this(stories, model, providerResponseId, providerCitations, null);
    }
}
