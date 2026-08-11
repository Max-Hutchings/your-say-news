package com.yoursay.unwrapped.validation;

import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.selection.CandidateRole;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnwrappedDraftValidatorTest {
    private static final String SOURCE_URL =
            "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes";
    private static final String CAVEAT =
            "This analysis describes patterns among people who voted on this post; "
                    + "it cannot know every individual's reason.";

    private final UnwrappedDraftValidator validator =
            new UnwrappedDraftValidator(new SourceUrlPolicy());

    @Test
    void acceptsACompleteDraftWhoseSourcesExactlyMatchProviderCitations() {
        assertDoesNotThrow(() -> validator.validate(request(), validDraft(), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsMissingPagesAndWrongOptionOrder() {
        assertEquals("UNWRAPPED_OPTION_PAGE_COUNT",
                failure(new UnwrappedResearchDraftV1(null, List.of(source())), List.of(SOURCE_URL)));

        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArgumentDraftV1 first = draft.pages().getFirst();
        UnwrappedArgumentDraftV1 wrongOption = new UnwrappedArgumentDraftV1(
                999L, first.headline(), first.selectedCohortIds(), first.paragraphs(), first.caveat());

        assertEquals("UNWRAPPED_OPTION_ORDER", failure(replaceFirst(draft, wrongOption),
                List.of(SOURCE_URL)));

        assertEquals("UNWRAPPED_OPTION_PAGE_COUNT", failure(
                new UnwrappedResearchDraftV1(List.of(draft.pages().getFirst()), draft.sources()),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_OPTION_PAGE_COUNT", failure(
                new UnwrappedResearchDraftV1(List.of(
                        draft.pages().getFirst(), draft.pages().getLast(), draft.pages().getLast()),
                        draft.sources()), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsMissingInventedDuplicateAndExcessCohorts() {
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();

        assertEquals("UNWRAPPED_COHORT_REQUIRED", failure(withSelectedCohorts(List.of()),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_INVENTED_COHORT", failure(withSelectedCohorts(
                List.of("ageRange=AGE_25_34")), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_TOO_MANY_COHORTS", failure(withSelectedCohorts(
                List.of(first.selectedCohortIds().getFirst(), first.selectedCohortIds().getFirst())),
                List.of(SOURCE_URL)));
    }

    @Test
    void rejectsThreeDistinctSelectedCohortsEvenWhenAllWereAllowed() {
        UnwrappedResearchRequest request = requestWithThreeCohorts();
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        UnwrappedArgumentDraftV1 page = new UnwrappedArgumentDraftV1(
                first.optionId(), first.headline(),
                List.of("gender=MAN", "gender=WOMAN", "gender=NON_BINARY"),
                first.paragraphs(), first.caveat());
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(page), validDraft().sources());

        assertEquals("UNWRAPPED_TOO_MANY_COHORTS", assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(request, draft, List.of(SOURCE_URL))).getMessage());
    }

    @Test
    void acceptsAnEmptySelectionOnlyWhenTheOptionHasNoCandidate() {
        UnwrappedResearchRequest request = requestWithoutCohort();
        UnwrappedArgumentDraftV1 page = new UnwrappedArgumentDraftV1(
                101L,
                "Lower deductions make household budgets breathe easier",
                List.of(),
                List.of(
                        paragraph("People choosing lower deductions are likely to favour the change because more "
                                + "take-home pay offers immediate help with household costs. The option converts "
                                + "an abstract tax decision into money available for essential monthly spending."),
                        paragraph("Published figures show household costs remain elevated while wage growth varies. "
                                + "A visible reduction can therefore look like practical breathing room, even when "
                                + "voters disagree about the balance between taxation and services.")),
                CAVEAT);
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(page), List.of(source()));

        assertDoesNotThrow(() -> validator.validate(request, draft, List.of(SOURCE_URL)));
    }

    @Test
    void rejectsHeadlinesThatHideTheCohortOrBreakTheEditorialBudget() {
        assertEquals("UNWRAPPED_HEADLINE_COHORT", failure(withHeadline(
                "Why changing the current tax rules feels urgent"), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_HEADLINE_GENERIC", failure(withHeadline(
                "Why agreements among men favour changing tax rules"), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withHeadline(
                "Why men favour reform"), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withHeadline(
                "Why men facing immediate household financial pressure strongly favour changing the current "
                        + "national tax rules before the next difficult winter arrives"), List.of(SOURCE_URL)));
    }

    @Test
    void doesNotTreatMenAsNamedWhenTheHeadlineOnlyNamesWomen() {
        assertEquals("UNWRAPPED_HEADLINE_COHORT", failure(withHeadline(
                "Why women favour changing the current tax rules"), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsEveryProhibitedGenericHeadlineForm() {
        for (String headline : List.of(
                "Why agreement among men cannot settle tax policy",
                "Why agreements among men cannot settle tax policy",
                "Why disagreement among men cannot settle tax policy",
                "Why disagreements among men cannot settle tax policy")) {
            assertEquals("UNWRAPPED_HEADLINE_GENERIC",
                    failure(withHeadline(headline), List.of(SOURCE_URL)));
        }
    }

    @Test
    void acceptsHeadlinesAtTheSixAndEighteenWordBoundaries() {
        assertDoesNotThrow(() -> validator.validate(request(), withHeadline(
                "Why men favour changing tax rules"), List.of(SOURCE_URL)));
        assertDoesNotThrow(() -> validator.validate(request(), withHeadline(
                "Why men under immediate household financial pressure strongly favour changing the current "
                        + "national tax rules this difficult winter"), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsHeadlinesImmediatelyOutsideTheSixAndEighteenWordBoundaries() {
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withHeadline(
                "Why men favour tax reform"), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withHeadline(
                "Why men under immediate household financial pressure strongly favour changing the current "
                        + "national tax rules during this difficult winter"), List.of(SOURCE_URL)));
    }

    @Test
    void acceptsEveryExplanatoryTermPromisedByTheOutputContract() {
        assertDoesNotThrow(() -> validator.validate(request(), withParagraphs(List.of(
                paragraph("Higher deductions led men towards this option as household budgets tightened and "
                        + "monthly costs rose. The observed voting pattern links immediate take-home pay with "
                        + "the practical policy choice without claiming private knowledge."),
                paragraph("Published figures show household costs remain elevated while wage growth varies. "
                        + "That national context gives reviewers relevant evidence for the pressures surrounding "
                        + "the option and the aggregate result."))), List.of(SOURCE_URL)));
        assertDoesNotThrow(() -> validator.validate(request(), withParagraphs(List.of(
                paragraph("Rising household costs drove men towards this option as deductions reduced the money "
                        + "available for monthly essentials. The observed voting pattern links take-home pay with "
                        + "the practical choice without claiming private knowledge."),
                paragraph("Published figures show household costs remain elevated while wage growth varies. "
                        + "That national context gives reviewers relevant evidence for the pressures surrounding "
                        + "the option and the aggregate result."))), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsIdentityBearingAndPrivateKnowledgeProse() {
        for (String unsafeText : List.of(
                "Contact jane.smith@example.com for details.",
                "One voter was born on 14 March 1989.",
                "One voter was born on March 14, 1989.",
                "One voter was born on 14th March 1989.",
                "One voter was born on 14/03/1989.",
                "One voter was born on 1989-03-14.",
                "Jane Smith voted for this option.",
                "Jane Smith chose this option.",
                "Jane Smith selected this option.",
                "Jane Smith supported this option.",
                "Jane Smith opposed this option.",
                "Jane Smith favoured this option.",
                "Jane Smith favored this option.",
                "Jane Smith was among the voters who favoured this option.",
                "We know every individual voted this way because of household costs.",
                "We know why every voter chose this option.",
                "The data proves why each voter selected this option.",
                "We can identify the voter behind this choice.")) {
            assertEquals("UNWRAPPED_PII_RISK", failure(
                    withFirstParagraphPrefix(unsafeText), List.of(SOURCE_URL)));
        }
    }

    @Test
    void rejectsIdentityBearingTextInHeadlines() {
        for (String headline : List.of(
                "Why men like Jane Smith voted for tax reform",
                "Why Jane voted alongside men for tax reform")) {
            assertEquals("UNWRAPPED_PII_RISK",
                    failure(withHeadline(headline), List.of(SOURCE_URL)));
        }
    }

    @Test
    void rejectsMalformedUnsupportedOrUnexplainedParagraphs() {
        UnwrappedArticleParagraphDraftV2 first = validDraft().pages().getFirst()
                .paragraphs().getFirst();

        assertEquals("UNWRAPPED_PARAGRAPH_COUNT", failure(withParagraphs(List.of(first)),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_UNSOURCED", failure(withParagraphs(List.of(
                new UnwrappedArticleParagraphDraftV2(first.text(), List.of("unknown-source")),
                validDraft().pages().getFirst().paragraphs().getLast())), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_EXPLANATION_MISSING", failure(withParagraphs(List.of(
                paragraph("Men appear more often among voters choosing the tax change. The result is notable "
                        + "and aligns with current discussion about household finances and public policy. "
                        + "Readers can compare this pattern with other recent local voting results."),
                paragraph("Published figures describe elevated household costs and uneven wage growth. These "
                        + "facts provide context for the vote and show the subject remains important. "
                        + "The latest bulletin also records variation between regions and household types."))),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_POPULATION_INFERENCE", failure(withParagraphs(List.of(
                paragraph("Men are likely to favour changing tax rules because deductions feel immediate. "
                        + "This audience represents the national population and therefore establishes how all "
                        + "men think about the policy."),
                paragraph("Published evidence shows household costs remain elevated. The policy may therefore "
                        + "feel like practical breathing room rather than an abstract ideological choice."))),
                List.of(SOURCE_URL)));

        UnwrappedArticleParagraphDraftV2 second = validDraft().pages().getFirst()
                .paragraphs().getLast();
        assertEquals("UNWRAPPED_PARAGRAPH_UNSOURCED", failure(withParagraphs(List.of(
                new UnwrappedArticleParagraphDraftV2(first.text(), List.of()), second)),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_UNSOURCED", failure(withParagraphs(List.of(
                new UnwrappedArticleParagraphDraftV2(first.text(), null), second)),
                List.of(SOURCE_URL)));
    }

    @Test
    void rejectsEveryGovernedPopulationInferenceForm() {
        for (String unsafeText : List.of(
                "This voting group mirrors the national population.",
                "This voting group reflects public opinion.",
                "The analysis generalises these results to the public.",
                "This group shows what the public thinks.")) {
            assertEquals("UNWRAPPED_POPULATION_INFERENCE", failure(
                    withFirstParagraphPrefix(unsafeText), List.of(SOURCE_URL)));
        }
    }

    @Test
    void rejectsNegatedExplanationsThatOnlyContainCausalKeywords() {
        UnwrappedResearchDraftV1 draft = withParagraphs(List.of(
                paragraph("There is no reason to infer the motivation of these voters from this aggregate. "
                        + "The result records a voting pattern but offers no supported account of the choice. "
                        + "Reviewers should not treat a keyword as an explanation."),
                paragraph("Published figures describe household costs and uneven wage growth across regions. "
                        + "Those statistics provide external context for the policy debate while remaining "
                        + "separate from the observed aggregate voting result.")));

        assertEquals("UNWRAPPED_EXPLANATION_MISSING", failure(draft, List.of(SOURCE_URL)));
    }

    @Test
    void acceptsThreeParagraphsAndRejectsFour() {
        assertDoesNotThrow(() -> validator.validate(request(), withParagraphs(List.of(
                paragraph(words(17)), paragraph(words(17)), paragraph(words(16)))),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_COUNT", failure(withParagraphs(List.of(
                paragraph(words(13)), paragraph(words(13)), paragraph(words(13)),
                paragraph(words(13)))), List.of(SOURCE_URL)));
    }

    @Test
    void rejectsNullAndBlankNestedStructuresWithStableCodes() {
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        assertEquals("UNWRAPPED_DRAFT_MISSING", failure(null, List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_OPTION_ORDER", failure(
                new UnwrappedResearchDraftV1(
                        java.util.Arrays.asList(null, validDraft().pages().getLast()),
                        validDraft().sources()), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_HEADLINE_GENERIC", failure(replaceFirst(validDraft(),
                new UnwrappedArgumentDraftV1(first.optionId(), null,
                        first.selectedCohortIds(), first.paragraphs(), first.caveat())),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_COHORTS_MISSING", failure(replaceFirst(validDraft(),
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(), null,
                        first.paragraphs(), first.caveat())), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_COUNT", failure(replaceFirst(validDraft(),
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(),
                        first.selectedCohortIds(), null, first.caveat())), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_MISSING", failure(withParagraphs(java.util.Arrays.asList(
                null, paragraph(words(50)))), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PARAGRAPH_MISSING", failure(withParagraphs(List.of(
                new UnwrappedArticleParagraphDraftV2("   ", List.of("source-1")),
                paragraph(words(50)))), List.of(SOURCE_URL)));

        assertEquals("UNWRAPPED_SOURCES_MISSING", failure(
                new UnwrappedResearchDraftV1(validDraft().pages(), null), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_SOURCE_ID", failure(
                new UnwrappedResearchDraftV1(validDraft().pages(),
                        java.util.Arrays.asList((UnwrappedSourceDraftV1) null)),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_SOURCE_ID", failure(withSources(List.of(
                new UnwrappedSourceDraftV1(null, SOURCE_URL,
                        "Office for National Statistics", "Public sector finances",
                        SourceClassification.OFFICIAL))), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_SOURCE_ID", failure(withSources(List.of(
                new UnwrappedSourceDraftV1("   ", SOURCE_URL,
                        "Office for National Statistics", "Public sector finances",
                        SourceClassification.OFFICIAL))), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING", failure(validDraft(), null));
    }

    @Test
    void enforcesArticleWordBoundariesAndTheExactCaveat() {
        assertEquals("UNWRAPPED_ARTICLE_WORDS", failure(withParagraphs(List.of(
                paragraph(words(24)), paragraph(words(25)))), List.of(SOURCE_URL)));
        assertDoesNotThrow(() -> validator.validate(request(), withParagraphs(List.of(
                paragraph(words(25)), paragraph(words(25)))), List.of(SOURCE_URL)));
        assertDoesNotThrow(() -> validator.validate(request(), withParagraphs(List.of(
                paragraph(words(50)), paragraph(words(50)))), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_ARTICLE_WORDS", failure(withParagraphs(List.of(
                paragraph(words(50)), paragraph(words(51)))), List.of(SOURCE_URL)));

        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        UnwrappedArgumentDraftV1 wrongCaveat = new UnwrappedArgumentDraftV1(
                first.optionId(), first.headline(), first.selectedCohortIds(), first.paragraphs(),
                "This may not represent everyone.");
        assertEquals("UNWRAPPED_OBSERVED_CAVEAT", failure(replaceFirst(validDraft(), wrongCaveat),
                List.of(SOURCE_URL)));
    }

    @Test
    void rejectsSourcesThatWereNotReturnedByTheProvider() {
        assertEquals("UNWRAPPED_SOURCE_NOT_PROVIDER_CITATION",
                failure(validDraft(), List.of("https://www.gov.uk/different-source")));
        assertEquals("UNWRAPPED_PROVIDER_CITATIONS_MISSING", failure(validDraft(), List.of()));
    }

    @Test
    void rejectsUnsafeDuplicateAndExcessSources() {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedSourceDraftV1 unsafe = new UnwrappedSourceDraftV1(
                "source-1", "http://www.ons.gov.uk/unsafe", "Office for National Statistics",
                "Public sector finances", SourceClassification.OFFICIAL);
        assertEquals("UNWRAPPED_SOURCE_URL_UNSAFE", failure(
                new UnwrappedResearchDraftV1(draft.pages(), List.of(unsafe)),
                List.of(unsafe.url())));

        assertEquals("UNWRAPPED_DUPLICATE_SOURCE_ID", failure(
                new UnwrappedResearchDraftV1(draft.pages(), List.of(source(), source())),
                List.of(SOURCE_URL)));

        List<UnwrappedSourceDraftV1> sources = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        for (int index = 1; index <= 21; index++) {
            String url = "https://www.ons.gov.uk/source-" + index;
            sources.add(new UnwrappedSourceDraftV1("source-" + index, url,
                    "Office for National Statistics", "Statistical bulletin " + index,
                    SourceClassification.OFFICIAL));
            citations.add(url);
        }
        assertEquals("UNWRAPPED_TOO_MANY_SOURCES", failure(
                new UnwrappedResearchDraftV1(draft.pages(), sources), citations));
    }

    @Test
    void enforcesEverySourceCollectionRuleIndependently() {
        UnwrappedResearchDraftV1 draft = validDraft();
        assertEquals("UNWRAPPED_SOURCES_MISSING", failure(
                new UnwrappedResearchDraftV1(draft.pages(), List.of()), List.of(SOURCE_URL)));

        UnwrappedSourceDraftV1 duplicateUrl = new UnwrappedSourceDraftV1(
                "source-2", SOURCE_URL, "UK Statistics Authority",
                "Independent statistical oversight", SourceClassification.OFFICIAL);
        assertEquals("UNWRAPPED_DUPLICATE_SOURCE_URL", failure(
                new UnwrappedResearchDraftV1(draft.pages(), List.of(source(), duplicateUrl)),
                List.of(SOURCE_URL)));

        String unusedUrl = "https://www.gov.uk/unused-source";
        UnwrappedSourceDraftV1 unused = new UnwrappedSourceDraftV1(
                "source-2", unusedUrl, "UK Government",
                "Unused policy publication", SourceClassification.OFFICIAL);
        assertEquals("UNWRAPPED_UNUSED_SOURCE", failure(
                new UnwrappedResearchDraftV1(draft.pages(), List.of(source(), unused)),
                List.of(SOURCE_URL, unusedUrl)));

        assertEquals("UNWRAPPED_SOURCE_METADATA", failure(withSources(List.of(
                sourceWithMetadata("", "Public sector finances", SourceClassification.OFFICIAL))),
                List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_SOURCE_METADATA", failure(withSources(List.of(
                sourceWithMetadata("Office for National Statistics", "x",
                        SourceClassification.OFFICIAL))), List.of(SOURCE_URL)));
        assertEquals("UNWRAPPED_SOURCE_METADATA", failure(withSources(List.of(
                sourceWithMetadata("Office for National Statistics", "Public sector finances", null))),
                List.of(SOURCE_URL)));
    }

    @Test
    void acceptsSourceMetadataAtItsExactLengthBoundaries() {
        for (UnwrappedSourceDraftV1 source : List.of(
                sourceWithMetadata("UK", "Data", SourceClassification.OFFICIAL),
                sourceWithMetadata("P".repeat(256), "T".repeat(512),
                        SourceClassification.ACADEMIC))) {
            assertDoesNotThrow(() -> validator.validate(request(),
                    withSources(List.of(source)), List.of(SOURCE_URL)));
        }
    }

    @Test
    void rejectsSourceMetadataImmediatelyOutsideItsLengthBoundaries() {
        for (UnwrappedSourceDraftV1 source : List.of(
                sourceWithMetadata("P", "Public sector finances", SourceClassification.OFFICIAL),
                sourceWithMetadata("P".repeat(257), "Public sector finances",
                        SourceClassification.OFFICIAL),
                sourceWithMetadata("Office for National Statistics", "Tit",
                        SourceClassification.OFFICIAL),
                sourceWithMetadata("Office for National Statistics", "T".repeat(513),
                        SourceClassification.OFFICIAL))) {
            assertEquals("UNWRAPPED_SOURCE_METADATA", failure(
                    withSources(List.of(source)), List.of(SOURCE_URL)));
        }
    }

    @Test
    void acceptsExactlyTwentyDistinctReferencedSources() {
        List<UnwrappedSourceDraftV1> sources = new ArrayList<>();
        List<String> citations = new ArrayList<>();
        List<String> sourceIds = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            String id = "source-" + index;
            String url = "https://www.ons.gov.uk/source-" + index;
            sources.add(new UnwrappedSourceDraftV1(id, url,
                    "Office for National Statistics", "Statistical bulletin " + index,
                    SourceClassification.OFFICIAL));
            citations.add(url);
            sourceIds.add(id);
        }
        UnwrappedResearchDraftV1 original = validDraft();
        List<UnwrappedArgumentDraftV1> pages = original.pages().stream()
                .map(page -> new UnwrappedArgumentDraftV1(
                        page.optionId(), page.headline(), page.selectedCohortIds(),
                        List.of(
                                new UnwrappedArticleParagraphDraftV2(
                                        page.paragraphs().getFirst().text(), sourceIds.subList(0, 10)),
                                new UnwrappedArticleParagraphDraftV2(
                                        page.paragraphs().getLast().text(), sourceIds.subList(10, 20))),
                        page.caveat()))
                .toList();

        assertDoesNotThrow(() -> validator.validate(request(),
                new UnwrappedResearchDraftV1(pages, sources), citations));
    }

    private String failure(UnwrappedResearchDraftV1 draft, List<String> citations) {
        return assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(), draft, citations)).getMessage();
    }

    private static UnwrappedResearchDraftV1 validDraft() {
        return new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(
                        101L,
                        "Why men favour changing the current tax rules",
                        List.of("gender=MAN"),
                        List.of(
                                paragraph("Men are likely to favour changing the tax rules because higher deductions "
                                        + "leave less room in monthly budgets. The voting pattern suggests immediate "
                                        + "take-home pay feels more urgent than benefits arriving later."),
                                paragraph("Published figures show household costs remain elevated while wage growth "
                                        + "varies. For these voters, a visible reduction may therefore look like "
                                        + "practical breathing room rather than an abstract ideological choice.")),
                        CAVEAT),
                new UnwrappedArgumentDraftV1(
                        102L,
                        "Why women favour keeping essential public services funded",
                        List.of("gender=WOMAN"),
                        List.of(
                                paragraph("Women are likely to favour keeping public services funded because reliable "
                                        + "care, transport and local provision can shape household resilience. The "
                                        + "voting pattern makes protecting shared systems a direct concern."),
                                paragraph("Published evidence shows service reductions often transfer time and financial "
                                        + "costs back to families. Maintaining funding may therefore preserve practical "
                                        + "support that would be expensive or impossible to replace privately.")),
                        CAVEAT)),
                List.of(source()));
    }

    private static UnwrappedResearchRequest request() {
        return new UnwrappedResearchRequest(
                42L, "A factual policy summary.", "Should the policy change?", "GB",
                100, "sha256:fixture",
                List.of(option(101L, "Change the tax rules", "gender=MAN", "Men", "MAN"),
                        option(102L, "Keep public services funded", "gender=WOMAN", "Women", "WOMAN")));
    }

    private static UnwrappedResearchRequest requestWithoutCohort() {
        return new UnwrappedResearchRequest(
                42L, "A factual policy summary.", "Should the policy change?", "GB",
                100, "sha256:fixture", List.of(new OptionBriefV1(
                new VoteOptionDto(101L, "Change the tax rules", 0, null),
                60, 60, List.of(), List.of("Write a researched option argument."),
                "No privacy-safe characteristic group is available.")));
    }

    private static UnwrappedResearchRequest requestWithThreeCohorts() {
        List<SelectedCohortV1> cohorts = List.of(
                selectedCohort("gender=MAN", "Men", "MAN"),
                selectedCohort("gender=WOMAN", "Women", "WOMAN"),
                selectedCohort("gender=NON_BINARY", "Non-binary people", "NON_BINARY"));
        return new UnwrappedResearchRequest(
                42L, "A factual policy summary.", "Should the policy change?", "GB",
                100, "sha256:fixture", List.of(new OptionBriefV1(
                new VoteOptionDto(101L, "Change the tax rules", 0, null),
                60, 60, cohorts, List.of("Explain the observed option-specific pattern."), null)));
    }

    private static OptionBriefV1 option(long id, String label, String cohortId,
                                        String displayName, String bucket) {
        SelectedCohortV1 cohort = selectedCohort(cohortId, displayName, bucket);
        return new OptionBriefV1(new VoteOptionDto(id, label, (int) id - 101, null),
                id == 101 ? 60 : 40, id == 101 ? 60 : 40,
                List.of(cohort), List.of("Explain the observed option-specific pattern."), null);
    }

    private static SelectedCohortV1 selectedCohort(
            String cohortId,
            String displayName,
            String bucket
    ) {
        return new SelectedCohortV1(
                cohortId, List.of(new CohortDimensionV1("gender", bucket)),
                CandidateRole.CORE_ANCHOR, "Broad core group", 50, 50,
                35, 58.3, 70, 10, 20, 56, 81, 0.01, displayName);
    }

    private static UnwrappedResearchDraftV1 withSelectedCohorts(List<String> selectedCohortIds) {
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        return replaceFirst(validDraft(), new UnwrappedArgumentDraftV1(
                first.optionId(), first.headline(), selectedCohortIds,
                first.paragraphs(), first.caveat()));
    }

    private static UnwrappedResearchDraftV1 withHeadline(String headline) {
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        return replaceFirst(validDraft(), new UnwrappedArgumentDraftV1(
                first.optionId(), headline, first.selectedCohortIds(),
                first.paragraphs(), first.caveat()));
    }

    private static UnwrappedResearchDraftV1 withParagraphs(
            List<UnwrappedArticleParagraphDraftV2> paragraphs) {
        UnwrappedArgumentDraftV1 first = validDraft().pages().getFirst();
        return replaceFirst(validDraft(), new UnwrappedArgumentDraftV1(
                first.optionId(), first.headline(), first.selectedCohortIds(),
                paragraphs, first.caveat()));
    }

    private static UnwrappedResearchDraftV1 withFirstParagraphPrefix(String prefix) {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArticleParagraphDraftV2 first = draft.pages().getFirst().paragraphs().getFirst();
        return withParagraphs(List.of(
                new UnwrappedArticleParagraphDraftV2(
                        prefix + " " + first.text(), first.sourceIds()),
                draft.pages().getFirst().paragraphs().getLast()));
    }

    private static UnwrappedResearchDraftV1 withSources(List<UnwrappedSourceDraftV1> sources) {
        return new UnwrappedResearchDraftV1(validDraft().pages(), sources);
    }

    private static UnwrappedResearchDraftV1 replaceFirst(
            UnwrappedResearchDraftV1 draft, UnwrappedArgumentDraftV1 first) {
        return new UnwrappedResearchDraftV1(
                List.of(first, draft.pages().getLast()), draft.sources());
    }

    private static UnwrappedArticleParagraphDraftV2 paragraph(String text) {
        return new UnwrappedArticleParagraphDraftV2(text, List.of("source-1"));
    }

    private static String words(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> index == 1 ? "because" : "word" + index)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static UnwrappedSourceDraftV1 source() {
        return new UnwrappedSourceDraftV1(
                "source-1", SOURCE_URL, "Office for National Statistics",
                "Public sector finances", SourceClassification.OFFICIAL);
    }

    private static UnwrappedSourceDraftV1 sourceWithMetadata(
            String publisher,
            String title,
            SourceClassification classification
    ) {
        return new UnwrappedSourceDraftV1(
                "source-1", SOURCE_URL, publisher, title, classification);
    }
}
