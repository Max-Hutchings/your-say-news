package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class UnwrappedDraftValidator {
    private static final String REQUIRED_CAVEAT =
            "This analysis describes patterns among people who voted on this post; "
                    + "it cannot know every individual's reason.";
    private static final Pattern GENERIC_HEADLINE = Pattern.compile(
            "\\b(agreements?|disagreements?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POPULATION_LANGUAGE = Pattern.compile(
            "(representative of|represents|mirrors|reflects).{0,60}"
                    + "(the )?(population|national opinion|public opinion|everyone)"
                    + "|generali[sz](?:e|es|ed).{0,40}(population|public|national)");
    private static final Pattern EXPLANATORY_LANGUAGE = Pattern.compile(
            "\\b(because|reason|reasons|driven|motivated|prompted|shaped|explains?|"
                    + "stems? from|due to)\\b",
            Pattern.CASE_INSENSITIVE);

    private final SourceUrlPolicy urlPolicy;

    @Inject
    public UnwrappedDraftValidator(SourceUrlPolicy urlPolicy) {
        this.urlPolicy = urlPolicy;
    }

    public void validate(UnwrappedResearchRequest request, UnwrappedResearchDraftV1 draft,
                         List<String> providerCitations) {
        require(draft != null, "UNWRAPPED_DRAFT_MISSING");
        require(draft.pages() != null && draft.pages().size() == request.options().size(),
                "UNWRAPPED_OPTION_PAGE_COUNT");
        Map<String, UnwrappedSourceDraftV1> sources = uniqueSources(draft.sources());
        sources.values().forEach(this::validateSource);

        for (int index = 0; index < request.options().size(); index++) {
            validatePage(request.options().get(index), draft.pages().get(index), sources);
        }
    }

    private void validatePage(OptionBriefV1 brief, UnwrappedArgumentDraftV1 page,
                              Map<String, UnwrappedSourceDraftV1> sources) {
        require(page != null && brief.option().id().equals(page.optionId()),
                "UNWRAPPED_OPTION_ORDER");
        require(page.headline() != null && !GENERIC_HEADLINE.matcher(page.headline()).find(),
                "UNWRAPPED_HEADLINE_GENERIC");
        require(wordCount(page.headline()) >= 6 && wordCount(page.headline()) <= 10,
                "UNWRAPPED_HEADLINE_WORDS");
        require(page.selectedCohortIds() != null, "UNWRAPPED_COHORTS_MISSING");
        require(page.selectedCohortIds().size() <= 2
                        && new HashSet<>(page.selectedCohortIds()).size()
                        == page.selectedCohortIds().size(),
                "UNWRAPPED_TOO_MANY_COHORTS");
        Map<String, SelectedCohortV1> allowed = brief.candidates().stream()
                .collect(Collectors.toMap(SelectedCohortV1::cohortId, Function.identity()));
        require(allowed.keySet().containsAll(page.selectedCohortIds()),
                "UNWRAPPED_INVENTED_COHORT");
        if (allowed.isEmpty()) {
            require(page.selectedCohortIds().isEmpty(), "UNWRAPPED_INVENTED_COHORT");
        } else {
            require(!page.selectedCohortIds().isEmpty(), "UNWRAPPED_COHORT_REQUIRED");
            String normalizedHeadline = normalize(page.headline());
            require(page.selectedCohortIds().stream()
                            .map(allowed::get)
                            .anyMatch(cohort -> normalizedHeadline.contains(normalize(cohort.displayName()))),
                    "UNWRAPPED_HEADLINE_COHORT");
        }

        require(page.paragraphs() != null
                        && page.paragraphs().size() >= 2 && page.paragraphs().size() <= 3,
                "UNWRAPPED_PARAGRAPH_COUNT");
        int words = page.paragraphs().stream()
                .map(UnwrappedArticleParagraphDraftV2::text).mapToInt(UnwrappedDraftValidator::wordCount)
                .sum();
        require(words >= 50 && words <= 100, "UNWRAPPED_ARTICLE_WORDS");
        String article = page.paragraphs().stream()
                .map(UnwrappedArticleParagraphDraftV2::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "));
        require(EXPLANATORY_LANGUAGE.matcher(article).find(), "UNWRAPPED_EXPLANATION_MISSING");
        for (UnwrappedArticleParagraphDraftV2 paragraph : page.paragraphs()) {
            require(paragraph != null && paragraph.text() != null && !paragraph.text().isBlank(),
                    "UNWRAPPED_PARAGRAPH_MISSING");
            require(paragraph.sourceIds() != null && !paragraph.sourceIds().isEmpty()
                            && paragraph.sourceIds().stream().allMatch(sources::containsKey),
                    "UNWRAPPED_PARAGRAPH_UNSOURCED");
            require(!POPULATION_LANGUAGE.matcher(paragraph.text().toLowerCase(Locale.ROOT)).find(),
                    "UNWRAPPED_POPULATION_INFERENCE");
        }
        require(REQUIRED_CAVEAT.equals(page.caveat()), "UNWRAPPED_OBSERVED_CAVEAT");
    }

    private void validateSource(UnwrappedSourceDraftV1 source) {
        require(length(source.publisher(), 2, 256) && length(source.title(), 4, 512)
                        && source.classification() != null,
                "UNWRAPPED_SOURCE_METADATA");
        urlPolicy.validate(source.url());
    }

    private static Map<String, UnwrappedSourceDraftV1> uniqueSources(
            List<UnwrappedSourceDraftV1> sources) {
        require(sources != null && !sources.isEmpty(), "UNWRAPPED_SOURCES_MISSING");
        require(sources.stream().allMatch(source -> source != null && source.id() != null
                && !source.id().isBlank()), "UNWRAPPED_SOURCE_ID");
        return Map.copyOf(sources.stream().collect(Collectors.toMap(
                UnwrappedSourceDraftV1::id, Function.identity(), (left, right) -> {
                    throw new IllegalArgumentException("UNWRAPPED_DUPLICATE_SOURCE_ID");
                })));
    }

    private static int wordCount(String value) {
        if (value == null || value.isBlank()) return 0;
        return value.trim().split("\\s+").length;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('–', '-')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static boolean length(String value, int minimum, int maximum) {
        return value != null && value.trim().length() >= minimum && value.trim().length() <= maximum;
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
