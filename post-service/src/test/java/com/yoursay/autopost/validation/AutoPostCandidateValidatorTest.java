package com.yoursay.autopost.validation;

import com.yoursay.autopost.AutoPostRegion;
import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPostCandidateValidatorTest {

    private static final Instant WINDOW_END = Instant.parse("2026-08-20T12:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-08-19T12:00:00Z");
    private static final DiscoveredStorySource VALID_SOURCE = new DiscoveredStorySource(
            "https://www.reuters.com/world/uk/example-story", "Example story", "Reuters");

    private final AutoPostCandidateValidator validator = new AutoPostCandidateValidator();

    @Test
    void acceptsPopulatedOutputWithoutRevalidatingEditorialRequirements() {
        DiscoveredStory repeatedStory = new DiscoveredStory(
                99, AutoPostRegion.UK, "A populated headline", "A populated summary.",
                "repeated-story", WINDOW_START.minusSeconds(1),
                List.of(new DiscoveredStorySource("not-a-url", "A source title", "A publisher")));
        StoryDiscoveryResult result = new StoryDiscoveryResult(
                java.util.Collections.nCopies(10, repeatedStory),
                "grok-4.5", "response-42", List.of());

        assertDoesNotThrow(() -> validator.validateRequiredFields(result));
    }

    @Test
    void rejectsAnEmptyStoryList() {
        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validateRequiredFields(result(List.of())));

        assertEquals("AUTO_POST_STORIES_MISSING", error.code());
    }

    @Test
    void rejectsAProviderFailurePresentedAsOnePopulatedStory() {
        DiscoveredStory failurePresentedAsStory = story(
                AutoPostRegion.GLOBAL,
                "No qualified stories in supplied window",
                "Live search could not be completed with verifiable primary sources.",
                "no-qualified-stories",
                WINDOW_END,
                List.of(VALID_SOURCE));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validateRequiredFields(result(List.of(failurePresentedAsStory))));

        assertEquals("AUTO_POST_STORY_COUNT_INVALID", error.code());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingRequiredFields")
    void rejectsEveryMissingPersistenceField(
            String description,
            StoryDiscoveryResult result,
            String expectedCode
    ) {
        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validateRequiredFields(result));

        assertEquals(expectedCode, error.code());
    }

    private static Stream<Arguments> missingRequiredFields() {
        return Stream.of(
                Arguments.of("null result", null, "AUTO_POST_STORIES_MISSING"),
                Arguments.of("null story", result(listWithNull()), "AUTO_POST_STORY_MISSING"),
                Arguments.of("null region", result(List.of(story(null, "Headline", "Summary",
                        "story-key", WINDOW_END, List.of(VALID_SOURCE)))), "AUTO_POST_REGION_MISSING"),
                Arguments.of("blank headline", result(List.of(story(AutoPostRegion.US, " ",
                        "Summary", "story-key", WINDOW_END, List.of(VALID_SOURCE)))),
                        "AUTO_POST_HEADLINE_MISSING"),
                Arguments.of("blank summary", result(List.of(story(AutoPostRegion.US, "Headline",
                        " ", "story-key", WINDOW_END, List.of(VALID_SOURCE)))),
                        "AUTO_POST_SUMMARY_MISSING"),
                Arguments.of("blank deduplication key", result(List.of(story(AutoPostRegion.US,
                        "Headline", "Summary", " ", WINDOW_END, List.of(VALID_SOURCE)))),
                        "AUTO_POST_DEDUPLICATION_KEY_MISSING"),
                Arguments.of("null publication time", result(List.of(story(AutoPostRegion.US,
                        "Headline", "Summary", "story-key", null, List.of(VALID_SOURCE)))),
                        "AUTO_POST_PUBLISHED_AT_MISSING"),
                Arguments.of("empty sources", result(List.of(story(AutoPostRegion.US, "Headline",
                        "Summary", "story-key", WINDOW_END, List.of()))),
                        "AUTO_POST_SOURCES_MISSING"),
                Arguments.of("null source", result(List.of(story(AutoPostRegion.US, "Headline",
                        "Summary", "story-key", WINDOW_END, listWithNull()))),
                        "AUTO_POST_SOURCE_MISSING"),
                Arguments.of("blank source URL", resultWithSource(
                        new DiscoveredStorySource(" ", "Title", "Publisher")),
                        "AUTO_POST_SOURCE_URL_MISSING"),
                Arguments.of("blank source title", resultWithSource(
                        new DiscoveredStorySource("https://example.com/story", " ", "Publisher")),
                        "AUTO_POST_SOURCE_TITLE_MISSING"),
                Arguments.of("blank source publisher", resultWithSource(
                        new DiscoveredStorySource("https://example.com/story", "Title", " ")),
                        "AUTO_POST_SOURCE_PUBLISHER_MISSING")
        );
    }

    private static StoryDiscoveryResult resultWithSource(DiscoveredStorySource source) {
        return result(List.of(story(AutoPostRegion.GLOBAL, "Headline", "Summary", "story-key",
                WINDOW_END, List.of(source))));
    }

    private static StoryDiscoveryResult result(List<DiscoveredStory> stories) {
        return new StoryDiscoveryResult(stories, "grok-4.5", "response-42", List.of());
    }

    private static DiscoveredStory story(
            AutoPostRegion region,
            String headline,
            String summary,
            String deduplicationKey,
            Instant publishedAt,
            List<DiscoveredStorySource> sources
    ) {
        return new DiscoveredStory(
                1, region, headline, summary, deduplicationKey, publishedAt, sources);
    }

    private static <T> List<T> listWithNull() {
        return java.util.Collections.singletonList(null);
    }
}
