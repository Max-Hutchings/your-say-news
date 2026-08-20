package com.yoursay.autopost.validation;

import com.yoursay.autopost.AutoPostRegion;
import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AutoPostCandidateValidatorTest {

    private static final Instant WINDOW_END = Instant.parse("2026-08-20T12:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-08-19T12:00:00Z");

    private final AutoPostCandidateValidator validator = new AutoPostCandidateValidator();

    @Test
    void acceptsExactlyTenRankedUniqueStoriesAcrossAllRegionsWithVerifiedSources() {
        StoryDiscoveryResult result = result(stories());

        assertDoesNotThrow(() -> validator.validate(result, WINDOW_START, WINDOW_END));
    }

    @Test
    void rejectsTwoHeadlinesAboutTheSameUnderlyingStory() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        stories.set(7, story(8, AutoPostRegion.US, "Federal Reserve keeps rates unchanged",
                "fed-rate-decision", "https://www.reuters.com/world/us/fed-rates"));
        stories.set(8, story(9, AutoPostRegion.GLOBAL, "Markets react after Fed rate decision",
                "fed-rate-decision", "https://apnews.com/article/fed-rates"));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_DUPLICATE_STORY", error.code());
        assertEquals("Discovery returned duplicate underlying stories.", error.getMessage());
    }

    @Test
    void rejectsMatchingUnderlyingStoriesEvenWhenProviderKeysDiffer() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        stories.set(7, story(8, AutoPostRegion.US,
                "Federal Reserve keeps interest rates unchanged after policy meeting",
                "fed-decision-primary", "https://www.reuters.com/world/us/fed-rates"));
        stories.set(8, story(9, AutoPostRegion.GLOBAL,
                "Interest rates unchanged after Federal Reserve policy meeting",
                "markets-response-secondary", "https://apnews.com/article/fed-rates"));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_DUPLICATE_STORY", error.code());
    }

    @Test
    void rejectsAnythingOtherThanTenStories() {
        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories().subList(0, 9)), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_STORY_COUNT_INVALID", error.code());
    }

    @Test
    void rejectsRanksOutsideOneThroughTen() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        DiscoveredStory original = stories.getLast();
        stories.set(9, new DiscoveredStory(11, original.region(), original.headline(), original.summary(),
                original.deduplicationKey(), original.publishedAt(), original.sources()));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_RANK_INVALID", error.code());
    }

    @Test
    void rejectsSetsThatDoNotContainEveryRequestedRegion() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        stories.set(0, story(1, AutoPostRegion.GLOBAL, "G7 leaders agree disaster fund",
                "g7-disaster-fund", "https://www.bbc.com/news/world-g7-fund"));
        stories.set(3, story(4, AutoPostRegion.GLOBAL, "UN outlines new aid corridor",
                "un-aid-corridor", "https://news.un.org/en/story/aid-corridor"));
        stories.set(6, story(7, AutoPostRegion.GLOBAL, "Global rail group publishes safety review",
                "global-rail-safety-review", "https://uic.org/news/rail-safety-review"));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_REGION_MISSING", error.code());
    }

    @Test
    void rejectsAStoryOutsideTheExactTwentyFourHourWindow() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        DiscoveredStory original = stories.getFirst();
        stories.set(0, new DiscoveredStory(original.rank(), original.region(), original.headline(),
                original.summary(), original.deduplicationKey(), WINDOW_START.minusSeconds(1),
                original.sources()));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_STORY_OUTSIDE_WINDOW", error.code());
    }

    @Test
    void acceptsStoriesPublishedAtEitherWindowBoundary() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        DiscoveredStory first = stories.getFirst();
        DiscoveredStory last = stories.getLast();
        stories.set(0, new DiscoveredStory(first.rank(), first.region(), first.headline(), first.summary(),
                first.deduplicationKey(), WINDOW_START, first.sources()));
        stories.set(9, new DiscoveredStory(last.rank(), last.region(), last.headline(), last.summary(),
                last.deduplicationKey(), WINDOW_END, last.sources()));

        assertDoesNotThrow(() -> validator.validate(result(stories), WINDOW_START, WINDOW_END));
    }

    @Test
    void rejectsMalformedSourceUrls() {
        List<DiscoveredStory> stories = new ArrayList<>(stories());
        DiscoveredStory original = stories.getFirst();
        stories.set(0, new DiscoveredStory(original.rank(), original.region(), original.headline(),
                original.summary(), original.deduplicationKey(), original.publishedAt(),
                List.of(new DiscoveredStorySource("not-a-url", "Source", "Publisher"))));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result(stories), WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_SOURCE_INVALID", error.code());
    }

    @Test
    void rejectsADeclaredSourceThatWasNotReturnedByWebSearch() {
        StoryDiscoveryResult result = new StoryDiscoveryResult(stories(), "grok-4.5", "response-42",
                List.of("https://www.bbc.com/news/uk-budget"));

        AutoPostValidationException error = assertThrows(AutoPostValidationException.class,
                () -> validator.validate(result, WINDOW_START, WINDOW_END));

        assertEquals("AUTO_POST_SOURCE_NOT_VERIFIED", error.code());
    }

    private static StoryDiscoveryResult result(List<DiscoveredStory> stories) {
        return new StoryDiscoveryResult(stories, "grok-4.5", "response-42", stories.stream()
                .flatMap(story -> story.sources().stream())
                .map(DiscoveredStorySource::url)
                .toList());
    }

    private static List<DiscoveredStory> stories() {
        return List.of(
                story(1, AutoPostRegion.UK, "Chancellor sets out revised budget rules",
                        "uk-budget-rules", "https://www.bbc.com/news/uk-budget"),
                story(2, AutoPostRegion.US, "Senate advances national housing package",
                        "us-housing-package", "https://apnews.com/article/us-housing"),
                story(3, AutoPostRegion.GLOBAL, "G20 agrees cross-border climate finance plan",
                        "g20-climate-finance", "https://www.reuters.com/world/g20-climate"),
                story(4, AutoPostRegion.UK, "NHS publishes elective care recovery figures",
                        "nhs-elective-care", "https://www.england.nhs.uk/news/recovery"),
                story(5, AutoPostRegion.US, "Supreme Court hears digital privacy challenge",
                        "us-digital-privacy", "https://www.supremecourt.gov/oral_arguments/privacy"),
                story(6, AutoPostRegion.GLOBAL, "UN reports expansion of emergency food programme",
                        "un-food-programme", "https://news.un.org/en/story/food-programme"),
                story(7, AutoPostRegion.UK, "Rail regulator publishes punctuality review",
                        "uk-rail-review", "https://www.orr.gov.uk/rail-punctuality"),
                story(8, AutoPostRegion.US, "Federal Reserve keeps rates unchanged",
                        "fed-rate-decision", "https://www.federalreserve.gov/rates"),
                story(9, AutoPostRegion.GLOBAL, "WHO updates international outbreak response",
                        "who-outbreak-response", "https://www.who.int/news/outbreak-response"),
                story(10, AutoPostRegion.GLOBAL, "Major economies sign shipping emissions accord",
                        "shipping-emissions-accord", "https://www.imo.org/shipping-accord")
        );
    }

    private static DiscoveredStory story(int rank, AutoPostRegion region, String headline,
                                         String key, String sourceUrl) {
        return new DiscoveredStory(rank, region, headline,
                "A concise factual summary for " + headline.toLowerCase() + ".",
                key, WINDOW_END.minusSeconds(rank * 1_800L),
                List.of(new DiscoveredStorySource(sourceUrl, headline + " - source", "News desk")));
    }
}
