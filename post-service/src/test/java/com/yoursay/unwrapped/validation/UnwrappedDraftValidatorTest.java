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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnwrappedDraftValidatorTest {
    private static final String SOURCE =
            "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes";
    private final UnwrappedDraftValidator validator =
            new UnwrappedDraftValidator(new SourceUrlPolicy());

    @Test
    void acceptsCohortLedCausalAnalysisWithTwoParagraphsAndSixtyWords() {
        assertDoesNotThrow(() -> validator.validate(request(), validDraft(), List.of(SOURCE)));
    }

    @Test
    void acceptsDirectLikelyBecauseExplanationsInsteadOfRejectingCausalLanguage() {
        UnwrappedResearchDraftV1 draft = withFirstParagraph(
                "Men are likely to favour changing the tax rules because the selected cohort "
                        + "reports stronger exposure to rising deductions and less room in monthly budgets. "
                        + "That pressure makes an immediate reduction feel more valuable than distant benefits.");

        assertDoesNotThrow(() -> validator.validate(request(), draft, List.of(SOURCE)));
    }

    @Test
    void rejectsAnArticleThatNeverExplainsWhyTheCohortVotedThatWay() {
        UnwrappedResearchDraftV1 draft = withFirstParagraphs(
                paragraph("Men appear more often among voters choosing the tax change. The result is notable "
                        + "and may align with current discussion about household finances and public policy."),
                paragraph("Published figures describe elevated household costs and uneven wage growth. These facts "
                        + "provide useful context for the vote and show that the subject remains important."));

        assertEquals("UNWRAPPED_EXPLANATION_MISSING", failure(draft));
    }

    @Test
    void rejectsAHeadlineThatDoesNotNameItsSelectedCohort() {
        UnwrappedResearchDraftV1 draft = withFirstHeadline(
                "Why changing the tax rules feels urgent");

        assertEquals("UNWRAPPED_HEADLINE_COHORT", failure(draft));
    }

    @Test
    void rejectsGenericAgreementOrDisagreementHeadlines() {
        assertEquals("UNWRAPPED_HEADLINE_GENERIC", failure(withFirstHeadline(
                "Agreements highlight why men favour changing tax rules")));
        assertEquals("UNWRAPPED_HEADLINE_GENERIC", failure(withFirstHeadline(
                "Disagreements show why men reject current tax rules")));
    }

    @Test
    void allowsSupportWhenItIsAConcreteCohortLedHeadline() {
        assertDoesNotThrow(() -> validator.validate(request(), withFirstHeadline(
                "Why men support changing the tax rules"), List.of(SOURCE)));
    }

    @Test
    void rejectsAHeadlineOutsideTheSixToTenWordEditorialBudget() {
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withFirstHeadline(
                "Why men favour reform")));
        assertEquals("UNWRAPPED_HEADLINE_WORDS", failure(withFirstHeadline(
                "Why men under financial pressure are now much more likely to favour changing the tax rules")));
    }

    @Test
    void acceptsHeadlinesAtTheSixAndTenWordBoundaries() {
        assertDoesNotThrow(() -> validator.validate(request(), withFirstHeadline(
                "Why men favour changing tax rules"), List.of(SOURCE)));
        assertDoesNotThrow(() -> validator.validate(request(), withFirstHeadline(
                "Why men under pressure favour changing the current tax rules"), List.of(SOURCE)));
    }

    @Test
    void rejectsAnythingOtherThanTwoOrThreeParagraphs() {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArgumentDraftV1 first = draft.pages().getFirst();
        UnwrappedArgumentDraftV1 oneParagraph = new UnwrappedArgumentDraftV1(
                first.optionId(), first.headline(), first.selectedCohortIds(),
                List.of(first.paragraphs().getFirst()), first.caveat());

        assertEquals("UNWRAPPED_PARAGRAPH_COUNT", failure(replaceFirst(draft, oneParagraph)));

        assertDoesNotThrow(() -> validator.validate(request(), withFirstParagraphs(
                paragraph(words(17)), paragraph(words(17)), paragraph(words(16))),
                List.of(SOURCE)));
    }

    @Test
    void enforcesTheFiftyToOneHundredWordArticleBudget() {
        assertEquals("UNWRAPPED_ARTICLE_WORDS", failure(withFirstParagraphs(
                paragraph(words(24)), paragraph(words(25)))));
        assertEquals("UNWRAPPED_ARTICLE_WORDS", failure(withFirstParagraphs(
                paragraph(words(50)), paragraph(words(51)))));
        assertDoesNotThrow(() -> validator.validate(request(), withFirstParagraphs(
                paragraph(words(25)), paragraph(words(25))), List.of(SOURCE)));
        assertDoesNotThrow(() -> validator.validate(request(), withFirstParagraphs(
                paragraph(words(50)), paragraph(words(50))), List.of(SOURCE)));
    }

    @Test
    void rejectsMissingOrInventedCohorts() {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArgumentDraftV1 first = draft.pages().getFirst();
        assertEquals("UNWRAPPED_COHORT_REQUIRED", failure(replaceFirst(draft,
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(), List.of(),
                        first.paragraphs(), first.caveat()))));
        assertEquals("UNWRAPPED_INVENTED_COHORT", failure(replaceFirst(draft,
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(),
                        List.of("ageRange=AGE_25_34"), first.paragraphs(), first.caveat()))));
    }

    @Test
    void acceptsGeneralOptionArgumentsWhenNoCohortCandidateWasSupplied() {
        UnwrappedResearchRequest request = new UnwrappedResearchRequest(
                42L, "A factual policy summary.", "Should the policy change?", "GB",
                100, "sha256:fixture", List.of(new OptionBriefV1(
                        new VoteOptionDto(101L, "Change the tax rules", 0, null),
                        60, 60, List.of(), List.of(),
                        "No reliable demographic concentration passes the narration rules.")));
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
                                + "voters disagree about the longer-term balance between taxation and services.")),
                caveat());
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(page), List.of(source()));

        assertDoesNotThrow(() -> validator.validate(request, draft, List.of(SOURCE)));
    }

    @Test
    void rejectsParagraphsWithoutKnownSourceReferences() {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArticleParagraphDraftV2 dangling = new UnwrappedArticleParagraphDraftV2(
                draft.pages().getFirst().paragraphs().getFirst().text(), List.of("missing-source"));

        assertEquals("UNWRAPPED_PARAGRAPH_UNSOURCED", failure(withFirstParagraphs(
                dangling, draft.pages().getFirst().paragraphs().getLast())));
    }

    @Test
    void stillRejectsClaimsThatTheSampleRepresentsThePopulation() {
        UnwrappedResearchDraftV1 draft = withFirstParagraph(
                "Men are likely to favour changing the tax rules because household deductions feel immediate. "
                        + "This audience represents the national population and therefore establishes how all men think. "
                        + "That interpretation makes lower deductions appear more valuable than public spending.");

        assertEquals("UNWRAPPED_POPULATION_INFERENCE", failure(draft));
    }

    private String failure(UnwrappedResearchDraftV1 draft) {
        return assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(), draft, List.of(SOURCE))).getMessage();
    }

    private static UnwrappedResearchDraftV1 validDraft() {
        return new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(101L,
                        "Why men favour changing the tax rules",
                        List.of("gender=MAN"),
                        List.of(
                                paragraph("Men are likely to favour changing the tax rules because higher deductions "
                                        + "leave less room in monthly budgets. The selected voting pattern suggests "
                                        + "that immediate take-home pay feels more urgent than benefits arriving later."),
                                paragraph("Published figures show household costs have remained elevated while wage "
                                        + "growth varies sharply. For these voters, a visible reduction may therefore "
                                        + "look like practical breathing room rather than an abstract ideological choice.")),
                        caveat()),
                new UnwrappedArgumentDraftV1(102L,
                        "Why women favour keeping public services funded",
                        List.of("gender=WOMAN"),
                        List.of(
                                paragraph("Women are likely to favour keeping public services funded because reliable "
                                        + "care, transport and local provision can shape everyday household resilience. "
                                        + "The selected voting pattern makes protecting those shared systems a direct concern."),
                                paragraph("Published evidence shows service reductions often transfer time and financial "
                                        + "costs back to families. Maintaining funding can therefore feel like preserving "
                                        + "practical support that would be expensive or impossible to replace privately.")),
                        caveat())
        ), List.of(source()));
    }

    private static UnwrappedResearchRequest request() {
        return new UnwrappedResearchRequest(42L, "A factual policy summary.",
                "Should the policy change?", "GB", 100, "sha256:fixture",
                List.of(
                        option(101L, "Change the tax rules", "gender=MAN", "Men", "MAN"),
                        option(102L, "Keep public services funded", "gender=WOMAN", "Women", "WOMAN")));
    }

    private static OptionBriefV1 option(long id, String label, String cohortId,
                                        String displayName, String bucket) {
        SelectedCohortV1 cohort = new SelectedCohortV1(cohortId,
                List.of(new CohortDimensionV1("gender", bucket)),
                CandidateRole.CORE_ANCHOR, "Broad core group", 50, 50,
                35, 58.3, 70, 10, 20, 56, 81, 0.01, displayName);
        return new OptionBriefV1(new VoteOptionDto(id, label, (int) id - 101, null),
                id == 101 ? 60 : 40, id == 101 ? 60 : 40,
                List.of(cohort), List.of(), null);
    }

    private static UnwrappedResearchDraftV1 withFirstHeadline(String headline) {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArgumentDraftV1 first = draft.pages().getFirst();
        return replaceFirst(draft, new UnwrappedArgumentDraftV1(first.optionId(), headline,
                first.selectedCohortIds(), first.paragraphs(), first.caveat()));
    }

    private static UnwrappedResearchDraftV1 withFirstParagraph(String text) {
        UnwrappedResearchDraftV1 draft = validDraft();
        return withFirstParagraphs(paragraph(text), draft.pages().getFirst().paragraphs().getLast());
    }

    private static UnwrappedResearchDraftV1 withFirstParagraphs(
            UnwrappedArticleParagraphDraftV2... paragraphs) {
        UnwrappedResearchDraftV1 draft = validDraft();
        UnwrappedArgumentDraftV1 first = draft.pages().getFirst();
        return replaceFirst(draft, new UnwrappedArgumentDraftV1(first.optionId(), first.headline(),
                first.selectedCohortIds(), List.of(paragraphs), first.caveat()));
    }

    private static UnwrappedResearchDraftV1 replaceFirst(
            UnwrappedResearchDraftV1 draft, UnwrappedArgumentDraftV1 first) {
        return new UnwrappedResearchDraftV1(List.of(first, draft.pages().getLast()), draft.sources());
    }

    private static UnwrappedArticleParagraphDraftV2 paragraph(String text) {
        return new UnwrappedArticleParagraphDraftV2(text, List.of("source-1"));
    }

    private static String words(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(index -> index == 1 ? "because" : "word" + index)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private static String caveat() {
        return "This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.";
    }

    private static UnwrappedSourceDraftV1 source() {
        return new UnwrappedSourceDraftV1("source-1", SOURCE,
                "Office for National Statistics", "Public sector finances",
                SourceClassification.OFFICIAL);
    }
}
