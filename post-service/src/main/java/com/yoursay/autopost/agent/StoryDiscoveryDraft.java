package com.yoursay.autopost.agent;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

@Description("The ten most important non-duplicate stories in the requested 24-hour window")
public record StoryDiscoveryDraft(
        @Description("Exactly ten stories found through live web research")
        List<DiscoveredStoryDraft> stories
) {
}
