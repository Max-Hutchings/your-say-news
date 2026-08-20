package com.yoursay.autopost.validation;

import com.yoursay.autopost.AutoPostRegion;
import com.yoursay.autopost.agent.DiscoveredStory;
import com.yoursay.autopost.agent.DiscoveredStorySource;
import com.yoursay.autopost.agent.StoryDiscoveryResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class AutoPostCandidateValidator {

    private static final Set<String> HEADLINE_STOP_WORDS = Set.of(
            "a", "an", "and", "as", "at", "by", "for", "from", "in", "of", "on", "the", "to", "with");

    public void validate(StoryDiscoveryResult result, Instant windowStart, Instant windowEnd) {
        if (result == null || result.stories() == null || result.stories().size() != 10) {
            throw invalid("AUTO_POST_STORY_COUNT_INVALID", "Discovery must return exactly ten stories.");
        }
        Set<Integer> ranks = new HashSet<>();
        Set<String> keys = new HashSet<>();
        List<Set<String>> headlineTokens = new ArrayList<>();
        EnumSet<AutoPostRegion> regions = EnumSet.noneOf(AutoPostRegion.class);
        Set<String> providerCitations = canonicalUrls(result.providerCitations());

        for (DiscoveredStory story : result.stories()) {
            require(story != null, "AUTO_POST_STORY_INVALID", "Discovery returned an empty story.");
            require(story.rank() >= 1 && story.rank() <= 10 && ranks.add(story.rank()),
                    "AUTO_POST_RANK_INVALID", "Discovery returned invalid or duplicate ranks.");
            require(story.region() != null, "AUTO_POST_REGION_INVALID", "A story has no primary region.");
            regions.add(story.region());
            requireText(story.headline(), 300, "AUTO_POST_HEADLINE_INVALID", "A story headline is invalid.");
            requireText(story.summary(), 2_000, "AUTO_POST_SUMMARY_INVALID", "A story summary is invalid.");
            String key = normalizedKey(story.deduplicationKey());
            Set<String> currentHeadlineTokens = significantHeadlineTokens(story.headline());
            require(keys.add(key) && headlineTokens.stream().noneMatch(
                            priorHeadlineTokens -> substantiallyOverlaps(priorHeadlineTokens, currentHeadlineTokens)),
                    "AUTO_POST_DUPLICATE_STORY",
                    "Discovery returned duplicate underlying stories.");
            headlineTokens.add(currentHeadlineTokens);
            require(story.publishedAt() != null
                            && !story.publishedAt().isBefore(windowStart)
                            && !story.publishedAt().isAfter(windowEnd),
                    "AUTO_POST_STORY_OUTSIDE_WINDOW", "A story falls outside the requested window.");
            require(story.sources() != null && !story.sources().isEmpty(),
                    "AUTO_POST_SOURCE_INVALID", "Every story requires at least one source.");
            Set<String> storyUrls = new HashSet<>();
            for (DiscoveredStorySource source : story.sources()) {
                require(source != null, "AUTO_POST_SOURCE_INVALID", "A story contains an empty source.");
                String url = canonicalUrl(source.url());
                require(storyUrls.add(url), "AUTO_POST_SOURCE_INVALID", "A story contains duplicate sources.");
                require(providerCitations.contains(url), "AUTO_POST_SOURCE_NOT_VERIFIED",
                        "A story source was not returned by live web search.");
                requireText(source.title(), 512, "AUTO_POST_SOURCE_INVALID", "A source title is invalid.");
                requireText(source.publisher(), 256, "AUTO_POST_SOURCE_INVALID", "A source publisher is invalid.");
            }
        }
        require(ranks.equals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
                "AUTO_POST_RANK_INVALID", "Discovery must return ranks 1 through 10.");
        require(regions.equals(EnumSet.allOf(AutoPostRegion.class)),
                "AUTO_POST_REGION_MISSING", "Discovery must represent UK, US and global news.");
    }

    private static String normalizedKey(String value) {
        requireText(value, 160, "AUTO_POST_DEDUPLICATION_KEY_INVALID",
                "A story deduplication key is invalid.");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<String> significantHeadlineTokens(String headline) {
        Set<String> tokens = new HashSet<>(Arrays.asList(
                headline.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().split("\\s+")));
        tokens.removeAll(HEADLINE_STOP_WORDS);
        tokens.removeIf(token -> token.length() < 3);
        return tokens;
    }

    private static boolean substantiallyOverlaps(Set<String> first, Set<String> second) {
        int smallerSize = Math.min(first.size(), second.size());
        if (smallerSize < 4) {
            return first.equals(second) && !first.isEmpty();
        }
        long shared = first.stream().filter(second::contains).count();
        return shared * 20 >= smallerSize * 17;
    }

    private static Set<String> canonicalUrls(java.util.List<String> urls) {
        require(urls != null && !urls.isEmpty(), "AUTO_POST_SOURCE_NOT_VERIFIED",
                "Live web search returned no citations.");
        Set<String> result = new HashSet<>();
        urls.forEach(url -> result.add(canonicalUrl(url)));
        return result;
    }

    private static String canonicalUrl(String raw) {
        require(raw != null && !raw.isBlank(), "AUTO_POST_SOURCE_INVALID", "A source URL is invalid.");
        try {
            URI uri = URI.create(raw.trim()).normalize();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            require((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null,
                    "AUTO_POST_SOURCE_INVALID", "A source URL is invalid.");
            String path = uri.getPath();
            if (path != null && path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(scheme, uri.getUserInfo(), uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(), path, uri.getQuery(), null).toString();
        } catch (AutoPostValidationException error) {
            throw error;
        } catch (Exception error) {
            throw invalid("AUTO_POST_SOURCE_INVALID", "A source URL is invalid.");
        }
    }

    private static void requireText(String value, int max, String code, String message) {
        require(value != null && !value.isBlank() && value.trim().length() <= max, code, message);
    }

    private static void require(boolean condition, String code, String message) {
        if (!condition) {
            throw invalid(code, message);
        }
    }

    private static AutoPostValidationException invalid(String code, String message) {
        return new AutoPostValidationException(code, message);
    }
}
