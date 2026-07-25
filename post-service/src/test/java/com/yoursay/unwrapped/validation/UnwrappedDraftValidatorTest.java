package com.yoursay.unwrapped.validation;

import com.yoursay.posts.VoteOptionDto;
import com.yoursay.unwrapped.OptionBriefV1;
import com.yoursay.unwrapped.SelectedCohortV1;
import com.yoursay.unwrapped.CandidateRole;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.UnwrappedClaimDraftV1;
import com.yoursay.unwrapped.UnwrappedMode;
import com.yoursay.unwrapped.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.UnwrappedResearchRequest;
import com.yoursay.unwrapped.UnwrappedSourceDraftV1;
import com.yoursay.votes.CohortDimensionV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UnwrappedDraftValidatorTest {
    private static final String SOURCE = "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes";
    private final UnwrappedDraftValidator validator =
            new UnwrappedDraftValidator(new SourceUrlPolicy(), source -> {
            }, new SourceTrustPolicy());

    @Test
    void acceptsOrderedBalancedPagesWithClaimLevelProviderCitations() {
        assertDoesNotThrow(() -> validator.validate(request(UnwrappedMode.OBSERVED),
                validDraft(List.of("gender=MAN"), List.of()), List.of(SOURCE)));
    }

    @Test
    void rejectsAnObservedCohortThatDeterministicCodeDidNotShortlist() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED),
                        validDraft(List.of("ageRange=AGE_25_34"), List.of()), List.of(SOURCE)));
        assertEquals("UNWRAPPED_INVENTED_COHORT", error.getMessage());
    }

    @Test
    void rejectsPrivateOrNonHttpsResearchTargets() {
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                validDraft(List.of("gender=MAN"), List.of()).pages(),
                List.of(new UnwrappedSourceDraftV1("source-1", "https://127.0.0.1/admin",
                        "Internal", "Not public", SourceClassification.OTHER)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), draft,
                        List.of("https://127.0.0.1/admin")));
        assertEquals("UNWRAPPED_SOURCE_URL_PRIVATE", error.getMessage());
    }

    @Test
    void rejectsAStoryWhosePagesAndSourceListContainNoResearchEvidence() {
        UnwrappedResearchDraftV1 unsourced = new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(101L, "The strongest case for changing course",
                        List.of(), List.of(), List.of(),
                        "This page has persuasive prose but deliberately carries no supporting research at all.",
                        "This association describes this vote and does not prove individual motivation."),
                new UnwrappedArgumentDraftV1(102L, "The strongest case for keeping course",
                        List.of(), List.of(), List.of(),
                        "This page has persuasive prose but deliberately carries no supporting research at all.",
                        "This association describes this vote and does not prove individual motivation.")
        ), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), unsourced, List.of()));

        assertEquals("UNWRAPPED_PAGE_UNSOURCED", error.getMessage());
    }

    @Test
    void predictionCannotSmuggleObservedCohortsIntoTheStory() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.PREDICTION),
                        validDraft(List.of("gender=MAN"), List.of("Working-age people may support it.")),
                        List.of(SOURCE)));
        assertEquals("UNWRAPPED_PREDICTION_USED_OBSERVED_COHORT", error.getMessage());
    }

    @Test
    void rejectsReorderedOptionPages() {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedResearchDraftV1 reordered = new UnwrappedResearchDraftV1(
                List.of(valid.pages().get(1), valid.pages().get(0)), valid.sources());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), reordered, List.of(SOURCE)));

        assertEquals("UNWRAPPED_OPTION_ORDER", error.getMessage());
    }

    @Test
    void rejectsAClaimedUrlAbsentFromProviderCitationMetadata() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED),
                        validDraft(List.of("gender=MAN"), List.of()), List.of()));
        assertEquals("UNWRAPPED_SOURCE_NOT_PROVIDER_CITED", error.getMessage());
    }

    @Test
    void observedStoriesCannotContainPredictedCohortCopy() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED),
                        validDraft(List.of("gender=MAN"),
                                List.of("Working-age people may favour this option.")),
                        List.of(SOURCE)));
        assertEquals("UNWRAPPED_OBSERVED_CONTAINS_PREDICTION", error.getMessage());
    }

    @Test
    void rejectsExplicitCausalAndPopulationRepresentativeClaims() {
        UnwrappedResearchDraftV1 causal = draftWithClaim(
                "Being a man caused them to vote for this option.");
        assertEquals("UNWRAPPED_CAUSAL_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), causal, List.of(SOURCE)))
                .getMessage());

        UnwrappedResearchDraftV1 representative = draftWithClaim(
                "This Your Say sample represents the population.");
        assertEquals("UNWRAPPED_POPULATION_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), representative,
                        List.of(SOURCE))).getMessage());

        UnwrappedResearchDraftV1 causalParaphrase = draftWithClaim(
                "Men chose this because they pay more tax.");
        assertEquals("UNWRAPPED_CAUSAL_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), causalParaphrase,
                        List.of(SOURCE))).getMessage());

        UnwrappedResearchDraftV1 populationParaphrase = draftWithClaim(
                "This audience mirrors national opinion.");
        assertEquals("UNWRAPPED_POPULATION_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), populationParaphrase,
                        List.of(SOURCE))).getMessage());

        UnwrappedResearchDraftV1 disclaimerBypass = draftWithClaim(
                "This does not prove motivation, but gender drove the vote.");
        assertEquals("UNWRAPPED_CAUSAL_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), disclaimerBypass,
                        List.of(SOURCE))).getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://www.ons.gov.uk/data",
            "https://user:password@www.ons.gov.uk/data",
            "https://www.ons.gov.uk:8443/data",
            "https://localhost/data",
            "https://10.0.0.8/data",
            "https://169.254.10.2/data",
            "https://[::1]/data",
            "https://[fc00::1]/data",
            "https://[fd12:3456:789a::1]/data"
    })
    void rejectsEveryUnsafeUrlShape(String url) {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedResearchDraftV1 unsafe = new UnwrappedResearchDraftV1(valid.pages(),
                List.of(new UnwrappedSourceDraftV1("source-1", url,
                        "Unsafe", "Unsafe source", SourceClassification.OTHER)));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), unsafe, List.of(url)));
    }

    @Test
    void rejectsIpv6UniqueLocalSourcesWithThePrivateUrlCode() {
        String url = "https://[fc00::1]/data";
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedResearchDraftV1 unsafe = new UnwrappedResearchDraftV1(valid.pages(),
                List.of(new UnwrappedSourceDraftV1("source-1", url,
                        "Private publisher", "Private source", SourceClassification.OTHER)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), unsafe, List.of(url)));

        assertEquals("UNWRAPPED_SOURCE_URL_PRIVATE", error.getMessage());
    }

    @Test
    void rejectsNullCohortArraysBeforeAStoryCanReachTheClient() {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedArgumentDraftV1 first = valid.pages().getFirst();
        UnwrappedResearchDraftV1 nullObserved = new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(), null,
                        first.predictedCohorts(), first.contextClaims(), first.synthesis(), first.caveat()),
                valid.pages().get(1)), valid.sources());
        assertEquals("UNWRAPPED_COHORTS_MISSING", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), nullObserved,
                        List.of(SOURCE))).getMessage());

        UnwrappedResearchDraftV1 prediction = validDraft(List.of(), List.of("Likely supporters"));
        UnwrappedArgumentDraftV1 predictedFirst = prediction.pages().getFirst();
        UnwrappedResearchDraftV1 nullPrediction = new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(predictedFirst.optionId(), predictedFirst.headline(),
                        predictedFirst.usedCohortIds(), null, predictedFirst.contextClaims(),
                        predictedFirst.synthesis(), predictedFirst.caveat()),
                prediction.pages().get(1)), prediction.sources());
        assertEquals("UNWRAPPED_PREDICTIONS_MISSING", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.PREDICTION), nullPrediction,
                        List.of(SOURCE))).getMessage());
    }

    @Test
    void rejectsIncompleteSourceMetadataAndFailedReachabilityChecks() {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedResearchDraftV1 missingMetadata = new UnwrappedResearchDraftV1(valid.pages(),
                List.of(new UnwrappedSourceDraftV1("source-1", SOURCE, " ",
                        "Public sector finances", SourceClassification.OFFICIAL)));
        assertEquals("UNWRAPPED_SOURCE_METADATA", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.OBSERVED), missingMetadata,
                        List.of(SOURCE))).getMessage());

        UnwrappedDraftValidator unreachable = new UnwrappedDraftValidator(new SourceUrlPolicy(),
                source -> {
                    throw new IllegalArgumentException("UNWRAPPED_SOURCE_UNREACHABLE");
                }, new SourceTrustPolicy());
        assertEquals("UNWRAPPED_SOURCE_UNREACHABLE", assertThrows(IllegalArgumentException.class,
                () -> unreachable.validate(request(UnwrappedMode.OBSERVED), valid,
                        List.of(SOURCE))).getMessage());
    }

    @Test
    void allNonOfficialEvidenceMustBeDisclosedAndCannotSupportNumericClaims() {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedSourceDraftV1 other = new UnwrappedSourceDraftV1(
                "source-1", SOURCE, "Independent publisher",
                "Analysis of public finances", SourceClassification.OTHER);
        UnwrappedResearchDraftV1 allOther = new UnwrappedResearchDraftV1(
                valid.pages(), List.of(other));

        assertEquals("UNWRAPPED_NON_OFFICIAL_DISCLOSURE",
                assertThrows(IllegalArgumentException.class,
                        () -> validator.validate(request(UnwrappedMode.OBSERVED), allOther,
                                List.of(SOURCE))).getMessage());

        UnwrappedClaimDraftV1 numeric = new UnwrappedClaimDraftV1("claim-1",
                "The published measure rose by 12 percent.", List.of("source-1"), false);
        UnwrappedResearchDraftV1 numericOther = new UnwrappedResearchDraftV1(
                valid.pages().stream()
                        .map(page -> new UnwrappedArgumentDraftV1(page.optionId(), page.headline(),
                                page.usedCohortIds(), page.predictedCohorts(), List.of(numeric),
                                page.synthesis(), "This sample is an association and does not prove "
                                + "motivation. No official source was available for this claim."))
                        .toList(), List.of(other));
        assertEquals("UNWRAPPED_NUMERIC_CLAIM_SOURCE_QUALITY",
                assertThrows(IllegalArgumentException.class,
                        () -> validator.validate(request(UnwrappedMode.OBSERVED), numericOther,
                                List.of(SOURCE))).getMessage());
    }

    @Test
    void predictionLabelsAreBoundedAndPassTheSameLanguageGuardrails() {
        UnwrappedResearchDraftV1 valid =
                validDraft(List.of(), List.of("Working-age adults may support this option."));
        UnwrappedArgumentDraftV1 first = valid.pages().getFirst();
        UnwrappedResearchDraftV1 causalPrediction = new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(),
                        first.usedCohortIds(),
                        List.of("Men chose this because they pay more tax."),
                        first.contextClaims(), first.synthesis(), first.caveat()),
                valid.pages().get(1)), valid.sources());

        assertEquals("UNWRAPPED_CAUSAL_INFERENCE", assertThrows(IllegalArgumentException.class,
                () -> validator.validate(request(UnwrappedMode.PREDICTION), causalPrediction,
                        List.of(SOURCE))).getMessage());

        UnwrappedResearchDraftV1 tooManyPredictions = new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(first.optionId(), first.headline(),
                        first.usedCohortIds(), List.of("First predicted group",
                        "Second predicted group", "Third predicted group"),
                        first.contextClaims(), first.synthesis(), first.caveat()),
                valid.pages().get(1)), valid.sources());
        assertEquals("UNWRAPPED_TOO_MANY_PREDICTIONS",
                assertThrows(IllegalArgumentException.class,
                        () -> validator.validate(request(UnwrappedMode.PREDICTION),
                                tooManyPredictions, List.of(SOURCE))).getMessage());
    }

    private static UnwrappedResearchDraftV1 draftWithClaim(String statement) {
        UnwrappedResearchDraftV1 valid = validDraft(List.of("gender=MAN"), List.of());
        UnwrappedClaimDraftV1 claim =
                new UnwrappedClaimDraftV1("claim-1", statement, List.of("source-1"), false);
        return new UnwrappedResearchDraftV1(valid.pages().stream()
                .map(page -> new UnwrappedArgumentDraftV1(page.optionId(), page.headline(),
                        page.usedCohortIds(), page.predictedCohorts(), List.of(claim),
                        page.synthesis(), page.caveat()))
                .toList(), valid.sources());
    }

    private static UnwrappedResearchRequest request(UnwrappedMode mode) {
        OptionBriefV1 support = new OptionBriefV1(
                new VoteOptionDto(101L, "Support", 0, "AGREE"), 60, 60,
                List.of(new SelectedCohortV1("gender=MAN",
                        List.of(new CohortDimensionV1("gender", "MAN")),
                        CandidateRole.CORE_ANCHOR, "Broad core group", 50, 50,
                        35, 58.3, 70, 10, 20, 56, 81, 0.01)),
                List.of("What official data explains the case?"), List.of("No causation"), null);
        OptionBriefV1 oppose = new OptionBriefV1(
                new VoteOptionDto(102L, "Oppose", 1, "DISAGREE"), 40, 40,
                List.of(), List.of("What official data explains the case?"),
                List.of("No causation"), "No reliable concentration.");
        return new UnwrappedResearchRequest(mode, 42L, "A factual policy summary.",
                "Should the policy change?", "GB",
                mode == UnwrappedMode.PREDICTION ? 0 : 100,
                mode == UnwrappedMode.PREDICTION ? null : "sha256:fixture",
                List.of(support, oppose));
    }

    private static UnwrappedResearchDraftV1 validDraft(List<String> used, List<String> predicted) {
        UnwrappedClaimDraftV1 claim = new UnwrappedClaimDraftV1("claim-1",
                "Official statistics show a material and recent change in public costs.",
                List.of("source-1"), false);
        String caveat = predicted.isEmpty()
                ? "This association describes this vote and does not prove why any individual chose it."
                : "This prediction is tentative and may not describe the people who eventually vote.";
        return new UnwrappedResearchDraftV1(List.of(
                new UnwrappedArgumentDraftV1(101L, "The strongest case for changing course",
                        used, predicted, List.of(claim),
                        "Taken together, the observed pattern and wider evidence make a serious case for this option.",
                        caveat),
                new UnwrappedArgumentDraftV1(102L, "The strongest case for protecting the status quo",
                        List.of(), predicted, List.of(claim),
                        "Taken together, service outcomes and the available evidence make a serious case for this option.",
                        caveat)
        ), List.of(new UnwrappedSourceDraftV1("source-1", SOURCE,
                "Office for National Statistics", "Public sector finances",
                SourceClassification.OFFICIAL)));
    }
}
