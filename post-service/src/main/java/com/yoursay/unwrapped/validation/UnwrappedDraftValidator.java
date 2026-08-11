package com.yoursay.unwrapped.validation;

import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Enforces the machine-verifiable Unwrapped output contract before persistence or review. */
@ApplicationScoped
public class UnwrappedDraftValidator {
    private static final int MAXIMUM_SOURCE_COUNT = 20;
    private static final String REQUIRED_CAVEAT =
            "This analysis describes patterns among people who voted on this post; "
                    + "it cannot know every individual's reason.";
    private static final Pattern GENERIC_HEADLINE = Pattern.compile(
            "\\b(agreements?|disagreements?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern POPULATION_LANGUAGE = Pattern.compile(
            "(representative of|represents|mirrors|reflects).{0,60}"
                    + "(the )?(population|national opinion|public opinion|everyone)"
                    + "|generali[sz](?:e|es|ed).{0,40}(population|public|national)"
                    + "|shows? what (the )?(public|population) thinks?");
    private static final Pattern EXPLANATORY_LANGUAGE = Pattern.compile(
            "\\b(because|reason|reasons|led|drove|driven|motivated|prompted|shaped|explains?|"
                    + "stems? from|due to)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_ADDRESS = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXACT_NUMERIC_DATE = Pattern.compile(
            "\\b(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|"
                    + "\\d{4}-\\d{1,2}-\\d{1,2})\\b");
    private static final Pattern EXACT_WRITTEN_DATE = Pattern.compile(
            "\\b(?:\\d{1,2}(?:st|nd|rd|th)?\\s+(?:January|February|March|April|May|June|July|August|"
                    + "September|October|November|December)|"
                    + "(?:January|February|March|April|May|June|July|August|September|October|"
                    + "November|December)\\s+\\d{1,2},?)\\s+\\d{4}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_PERSON_VOTE = Pattern.compile(
            "\\b\\p{Lu}[\\p{L}'-]+(?:\\s+\\p{Lu}[\\p{L}'-]+)?\\s+"
                    + "(?:voted|chose|selected|supported|opposed|favoured|favored)\\b");
    private static final Pattern IDENTITY_LINKED_NAME = Pattern.compile(
            "\\b\\p{Lu}[\\p{L}'-]+\\s+\\p{Lu}[\\p{L}'-]+\\s+"
                    + "(?:was|is|appears?|featured)\\b.{0,40}\\b"
                    + "(?:voter|voters|voted|chose|selected|supported|opposed|favoured|favored)\\b");
    private static final Pattern PRIVATE_KNOWLEDGE = Pattern.compile(
            "\\b(?:(?:we|the data|this analysis) (?:know|knows|prove|proves) "
                    + "(?:why )?(?:every|each) (?:individual|voter)|"
                    + "(?:every|each) individual voted|we can identify (?:the )?voter)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATED_EXPLANATION = Pattern.compile(
            "\\b(?:no reason|without (?:a )?reason|cannot explain|can't explain|"
                    + "does not explain|doesn't explain|no supported account)\\b",
            Pattern.CASE_INSENSITIVE);

    private final SourceUrlPolicy urlPolicy;

    @Inject
    public UnwrappedDraftValidator(SourceUrlPolicy urlPolicy) {
        this.urlPolicy = urlPolicy;
    }

    public void validate(UnwrappedResearchRequest request, UnwrappedResearchDraftV1 draft,
                         List<String> providerCitations) {
        requireDraftShape(request, draft);
        Map<String, UnwrappedSourceDraftV1> sources =
                validatedSources(draft.sources(), providerCitations);
        Set<String> referencedSourceIds = validatePages(request, draft.pages(), sources);
        require(referencedSourceIds.containsAll(sources.keySet()), "UNWRAPPED_UNUSED_SOURCE");
    }

    private static void requireDraftShape(
            UnwrappedResearchRequest request,
            UnwrappedResearchDraftV1 draft
    ) {
        require(draft != null, "UNWRAPPED_DRAFT_MISSING");
        require(draft.pages() != null && draft.pages().size() == request.options().size(),
                "UNWRAPPED_OPTION_PAGE_COUNT");
    }

    private Map<String, UnwrappedSourceDraftV1> validatedSources(
            List<UnwrappedSourceDraftV1> sources,
            List<String> providerCitations
    ) {
        require(providerCitations != null && !providerCitations.isEmpty(),
                "UNWRAPPED_PROVIDER_CITATIONS_MISSING");
        require(sources != null && !sources.isEmpty(), "UNWRAPPED_SOURCES_MISSING");
        require(sources.size() <= MAXIMUM_SOURCE_COUNT, "UNWRAPPED_TOO_MANY_SOURCES");
        require(sources.stream().allMatch(source -> source != null
                        && source.id() != null && !source.id().isBlank()),
                "UNWRAPPED_SOURCE_ID");

        Map<String, UnwrappedSourceDraftV1> sourcesById = uniqueSourcesById(sources);
        Set<String> citedUrls = Set.copyOf(providerCitations);
        sourcesById.values().forEach(source -> validateSource(source, citedUrls));
        require(sourcesById.values().stream().map(UnwrappedSourceDraftV1::url).distinct().count()
                        == sourcesById.size(),
                "UNWRAPPED_DUPLICATE_SOURCE_URL");
        return sourcesById;
    }

    private static Map<String, UnwrappedSourceDraftV1> uniqueSourcesById(
            List<UnwrappedSourceDraftV1> sources
    ) {
        return Map.copyOf(sources.stream().collect(Collectors.toMap(
                UnwrappedSourceDraftV1::id,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("UNWRAPPED_DUPLICATE_SOURCE_ID");
                })));
    }

    private void validateSource(UnwrappedSourceDraftV1 source, Set<String> citedUrls) {
        require(length(source.publisher(), 2, 256)
                        && length(source.title(), 4, 512)
                        && source.classification() != null,
                "UNWRAPPED_SOURCE_METADATA");
        urlPolicy.validate(source.url());
        require(citedUrls.contains(source.url()), "UNWRAPPED_SOURCE_NOT_PROVIDER_CITATION");
    }

    private static Set<String> validatePages(
            UnwrappedResearchRequest request,
            List<UnwrappedArgumentDraftV1> pages,
            Map<String, UnwrappedSourceDraftV1> sources
    ) {
        Set<String> referencedSourceIds = new LinkedHashSet<>();
        for (int index = 0; index < request.options().size(); index++) {
            referencedSourceIds.addAll(validatePage(
                    request.options().get(index), pages.get(index), sources));
        }
        return referencedSourceIds;
    }

    private static Set<String> validatePage(
            OptionBriefV1 brief,
            UnwrappedArgumentDraftV1 page,
            Map<String, UnwrappedSourceDraftV1> sources
    ) {
        require(page != null && brief.option().id().equals(page.optionId()),
                "UNWRAPPED_OPTION_ORDER");
        validateHeadline(page.headline());
        validateSelectedCohorts(brief, page);
        Set<String> referencedSourceIds = validateArticle(page.paragraphs(), sources);
        require(REQUIRED_CAVEAT.equals(page.caveat()), "UNWRAPPED_OBSERVED_CAVEAT");
        return referencedSourceIds;
    }

    private static void validateHeadline(String headline) {
        require(headline != null && !GENERIC_HEADLINE.matcher(headline).find(),
                "UNWRAPPED_HEADLINE_GENERIC");
        require(wordCount(headline) >= 6 && wordCount(headline) <= 18,
                "UNWRAPPED_HEADLINE_WORDS");
        require(!containsIdentityRisk(headline), "UNWRAPPED_PII_RISK");
    }

    private static void validateSelectedCohorts(
            OptionBriefV1 brief,
            UnwrappedArgumentDraftV1 page
    ) {
        require(page.selectedCohortIds() != null, "UNWRAPPED_COHORTS_MISSING");
        require(page.selectedCohortIds().size() <= 2
                        && new HashSet<>(page.selectedCohortIds()).size()
                        == page.selectedCohortIds().size(),
                "UNWRAPPED_TOO_MANY_COHORTS");

        Map<String, SelectedCohortV1> allowedCohorts = brief.candidates().stream()
                .collect(Collectors.toMap(SelectedCohortV1::cohortId, Function.identity()));
        require(allowedCohorts.keySet().containsAll(page.selectedCohortIds()),
                "UNWRAPPED_INVENTED_COHORT");
        if (allowedCohorts.isEmpty()) {
            require(page.selectedCohortIds().isEmpty(), "UNWRAPPED_INVENTED_COHORT");
            return;
        }

        require(!page.selectedCohortIds().isEmpty(), "UNWRAPPED_COHORT_REQUIRED");
        require(headlineNamesSelectedCohort(
                        page.headline(), page.selectedCohortIds(), allowedCohorts),
                "UNWRAPPED_HEADLINE_COHORT");
    }

    private static boolean headlineNamesSelectedCohort(
            String headline,
            List<String> selectedCohortIds,
            Map<String, SelectedCohortV1> allowedCohorts
    ) {
        String normalizedHeadline = normalize(headline);
        return selectedCohortIds.stream()
                .map(allowedCohorts::get)
                .flatMap(UnwrappedDraftValidator::governedCohortNames)
                .map(UnwrappedDraftValidator::normalize)
                .anyMatch(name -> containsPhrase(normalizedHeadline, name));
    }

    private static Stream<String> governedCohortNames(SelectedCohortV1 cohort) {
        Stream<String> displayName = Stream.of(cohort.displayName());
        Stream<String> dimensionLabels = cohort.dimensions().stream()
                .map(CohortDimensionV1::label);
        return Stream.concat(displayName, dimensionLabels)
                .filter(value -> value != null && !value.isBlank());
    }

    private static Set<String> validateArticle(
            List<UnwrappedArticleParagraphDraftV2> paragraphs,
            Map<String, UnwrappedSourceDraftV1> sources
    ) {
        require(paragraphs != null && paragraphs.size() >= 2 && paragraphs.size() <= 3,
                "UNWRAPPED_PARAGRAPH_COUNT");
        int wordCount = paragraphs.stream()
                .map(paragraph -> paragraph == null ? null : paragraph.text())
                .mapToInt(UnwrappedDraftValidator::wordCount)
                .sum();
        require(wordCount >= 50 && wordCount <= 100, "UNWRAPPED_ARTICLE_WORDS");

        String article = paragraphs.stream()
                .map(paragraph -> paragraph == null ? null : paragraph.text())
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining(" "));
        require(!containsIdentityRisk(article), "UNWRAPPED_PII_RISK");
        require(EXPLANATORY_LANGUAGE.matcher(article).find()
                        && !NEGATED_EXPLANATION.matcher(article).find(),
                "UNWRAPPED_EXPLANATION_MISSING");

        Set<String> referencedSourceIds = new LinkedHashSet<>();
        paragraphs.forEach(paragraph -> validateParagraph(
                paragraph, sources, referencedSourceIds));
        return referencedSourceIds;
    }

    private static void validateParagraph(
            UnwrappedArticleParagraphDraftV2 paragraph,
            Map<String, UnwrappedSourceDraftV1> sources,
            Set<String> referencedSourceIds
    ) {
        require(paragraph != null && paragraph.text() != null && !paragraph.text().isBlank(),
                "UNWRAPPED_PARAGRAPH_MISSING");
        require(paragraph.sourceIds() != null && !paragraph.sourceIds().isEmpty()
                        && paragraph.sourceIds().stream().allMatch(sources::containsKey),
                "UNWRAPPED_PARAGRAPH_UNSOURCED");
        require(!POPULATION_LANGUAGE.matcher(
                        paragraph.text().toLowerCase(Locale.ROOT)).find(),
                "UNWRAPPED_POPULATION_INFERENCE");
        referencedSourceIds.addAll(paragraph.sourceIds());
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

    private static boolean containsPhrase(String normalizedText, String normalizedPhrase) {
        return (" " + normalizedText + " ").contains(" " + normalizedPhrase + " ");
    }

    private static boolean containsIdentityRisk(String article) {
        return EMAIL_ADDRESS.matcher(article).find()
                || EXACT_NUMERIC_DATE.matcher(article).find()
                || EXACT_WRITTEN_DATE.matcher(article).find()
                || NAMED_PERSON_VOTE.matcher(article).find()
                || IDENTITY_LINKED_NAME.matcher(article).find()
                || PRIVATE_KNOWLEDGE.matcher(article).find();
    }

    private static boolean length(String value, int minimum, int maximum) {
        return value != null && value.trim().length() >= minimum
                && value.trim().length() <= maximum;
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalArgumentException(code);
    }
}
