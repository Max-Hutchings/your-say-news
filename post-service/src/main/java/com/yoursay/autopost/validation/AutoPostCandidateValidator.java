package com.yoursay.autopost.validation;

import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import jakarta.enterprise.context.ApplicationScoped;

/** Checks only that the structured provider output contains values that persistence requires. */
@ApplicationScoped
public class AutoPostCandidateValidator {

    public void validateRequiredFields(StoryDiscoveryResult result) {
        require(result != null && result.stories() != null && !result.stories().isEmpty(),
                "AUTO_POST_STORIES_MISSING", "Discovery returned no stories.");
        for (DiscoveredStory story : result.stories()) {
            validateStory(story);
        }
        require(result.stories().size() == 10,
                "AUTO_POST_STORY_COUNT_INVALID",
                "Discovery must return exactly ten stories.");
    }

    private static void validateStory(DiscoveredStory story) {
        require(story != null, "AUTO_POST_STORY_MISSING", "Discovery returned an empty story.");
        require(story.region() != null,
                "AUTO_POST_REGION_MISSING", "A story has no primary region.");
        requireText(story.headline(),
                "AUTO_POST_HEADLINE_MISSING", "A story has no headline.");
        requireText(story.summary(),
                "AUTO_POST_SUMMARY_MISSING", "A story has no summary.");
        requireText(story.deduplicationKey(),
                "AUTO_POST_DEDUPLICATION_KEY_MISSING", "A story has no deduplication key.");
        require(story.publishedAt() != null,
                "AUTO_POST_PUBLISHED_AT_MISSING", "A story has no publication time.");
        require(story.sources() != null && !story.sources().isEmpty(),
                "AUTO_POST_SOURCES_MISSING", "A story has no sources.");
        story.sources().forEach(AutoPostCandidateValidator::validateSource);
    }

    private static void validateSource(DiscoveredStorySource source) {
        require(source != null,
                "AUTO_POST_SOURCE_MISSING", "A story contains an empty source.");
        requireText(source.url(),
                "AUTO_POST_SOURCE_URL_MISSING", "A source has no URL.");
        requireText(source.title(),
                "AUTO_POST_SOURCE_TITLE_MISSING", "A source has no title.");
        requireText(source.publisher(),
                "AUTO_POST_SOURCE_PUBLISHER_MISSING", "A source has no publisher.");
    }

    private static void requireText(String value, String code, String message) {
        require(value != null && !value.isBlank(), code, message);
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) {
            throw new AutoPostValidationException(code, message);
        }
    }
}
