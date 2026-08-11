package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.agent.UnwrappedResearchRequest;
import com.yoursay.unwrapped.agent.UnwrappedSystemPrompt;
import com.yoursay.unwrapped.selection.CandidateRole;
import com.yoursay.unwrapped.selection.OptionBriefV1;
import com.yoursay.unwrapped.selection.SelectedCohortV1;
import com.yoursay.votes.dto.CohortDimensionV1;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnwrappedBenchmarkContextTest {
    @Test
    void exposesTheExactAggregateOnlyRequestAndCharacteristicGroupsSentToTheModel() throws Exception {
        SelectedCohortV1 candidate = new SelectedCohortV1(
                "ageRange=AGE_25_34|gender=MAN",
                List.of(
                        new CohortDimensionV1("ageRange", "AGE_25_34"),
                        new CohortDimensionV1("gender", "MAN")),
                CandidateRole.INTERSECTION_DISCOVERY,
                "Strongest non-redundant two-characteristic intersection.",
                40, 32.0, 32, 42.7, 80.0, 20.0, 29.4,
                65.2, 89.5, 0.004, "Men aged 25 to 34");
        OptionBriefV1 option = new OptionBriefV1(
                new VoteOptionDto(71L, "Agree", 0, "AGREE"),
                75, 60.0, List.of(candidate),
                List.of("Explain why this selected cohort is likely to favour the option."), null);
        UnwrappedResearchRequest input = new UnwrappedResearchRequest(
                42L, "A measured summary.", "Should the city introduce the levy?",
                "UNITED_KINGDOM", 125, "aggregate-v1", List.of(option));
        UnwrappedResearchPreparation preparation = mock(UnwrappedResearchPreparation.class);
        when(preparation.prepare(42L)).thenReturn(
                new UnwrappedResearchPreparation.PreparedResearch(null, input));
        PostVotingConfigurationService posts = mock(PostVotingConfigurationService.class);
        when(posts.findByPostId(42L)).thenReturn(Optional.of(mock(PostVotingConfigurationDto.class)));
        ObjectMapper objectMapper = new ObjectMapper();
        UnwrappedServiceImpl service = new UnwrappedServiceImpl();
        service.researchPreparation = preparation;
        service.postService = posts;
        service.objectMapper = objectMapper;

        var context = service.benchmarkPrompt(42L);

        assertEquals(UnwrappedSystemPrompt.DEFAULT, context.systemPrompt());
        // Copying the whole prompt in here is what silently rotted when output-instructions.md was
        // rewritten, so pin the two things instead: the lines this service templates from *these*
        // options, and the individual clauses we must never lose from the prose.
        assertEquals(UnwrappedSystemPrompt.outputInstructions(List.of(71L)),
                context.outputInstructions());
        assertTrue(context.outputInstructions().contains("- Return exactly 1 pages."),
                context.outputInstructions());
        assertTrue(context.outputInstructions()
                        .contains("- Return pages in this exact `optionId` order: [71]."),
                context.outputInstructions());
        assertFalse(context.outputInstructions().contains("{{"),
                "Every placeholder must be resolved before the model sees the contract");
        // The prompt-level privacy control. Nothing else in the suite fails if this line is
        // deleted, so it is pinned verbatim here.
        assertTrue(context.outputInstructions().contains(
                        "- Do not identify individual voters using personal names, email addresses,"
                                + " exact dates of birth or identity-linked vote claims."),
                context.outputInstructions());
        // Must stay in step with UnwrappedDraftValidator's 6..18 headline check, or the model is
        // told one range and then rejected against another.
        assertTrue(context.outputInstructions().contains(
                        "- Headlines must be catchy, 6 to 18 words,"),
                context.outputInstructions());
        assertEquals(objectMapper.readTree(EXPECTED_INPUT_JSON).toString(),
                context.input().toString());
    }

    private static final String EXPECTED_INPUT_JSON = """
            {
              "postId": 42,
              "summary": "A measured summary.",
              "question": "Should the city introduce the levy?",
              "jurisdiction": "UNITED_KINGDOM",
              "canonicalVoteCount": 125,
              "aggregateVersion": "aggregate-v1",
              "options": [
                {
                  "option": {
                    "id": 71,
                    "label": "Agree",
                    "ordinal": 0,
                    "semanticKey": "AGREE"
                  },
                  "overallVoteCount": 75,
                  "overallVotePercentage": 60.0,
                  "candidates": [
                    {
                      "cohortId": "ageRange=AGE_25_34|gender=MAN",
                      "dimensions": [
                        { "axis": "ageRange", "bucket": "AGE_25_34" },
                        { "axis": "gender", "bucket": "MAN" }
                      ],
                      "role": "INTERSECTION_DISCOVERY",
                      "relevanceReason": "Strongest non-redundant two-characteristic intersection.",
                      "sampleSize": 40,
                      "populationSharePercentage": 32.0,
                      "optionVoteCount": 32,
                      "compositionPercentage": 42.7,
                      "propensityPercentage": 80.0,
                      "overIndexPercentagePoints": 20.0,
                      "differenceFromRestPercentagePoints": 29.4,
                      "wilson95Low": 65.2,
                      "wilson95High": 89.5,
                      "adjustedQValue": 0.004,
                      "displayName": "Men aged 25 to 34"
                    }
                  ],
                  "narrativeInstructions": [
                    "Explain why this selected cohort is likely to favour the option."
                  ],
                  "insufficientEvidence": null
                }
              ]
            }
            """;
}
