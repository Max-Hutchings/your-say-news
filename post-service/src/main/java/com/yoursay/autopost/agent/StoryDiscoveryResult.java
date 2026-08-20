package com.yoursay.autopost.agent;

import java.util.List;

public record StoryDiscoveryResult(
        List<DiscoveredStory> stories,
        String model,
        String providerResponseId,
        List<String> providerCitations
) {
}
