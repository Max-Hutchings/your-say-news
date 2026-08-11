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
        assertEquals(EXPECTED_OUTPUT_INSTRUCTIONS, context.outputInstructions());
        assertEquals(objectMapper.readTree(EXPECTED_INPUT_JSON).toString(),
                context.input().toString());
    }

    private static final String EXPECTED_OUTPUT_INSTRUCTIONS = """
            # Required output contract

            - Return exactly 1 pages.
            - Return pages in this exact `optionId` order: [71].
            - Include every `optionId` exactly once; do not merge or omit options.
            - When an option supplies cohort candidates, select one or two of their IDs and name a
              selected cohort in the headline using its supplied `displayName`.
            - When an option supplies no cohort candidates, return an empty `selectedCohortIds` list,
              write the strongest general researched case for that option, and do not invent a cohort.
            - Headlines must be catchy, 6 to 10 words, and must not use agreement or disagreement.
            - Write two or three paragraphs totalling 50 to 100 words for every page.
            - In those paragraphs, explain why the selected cohort, or voters choosing the option
              when no cohort is supplied, are likely to favour that option.
            - Direct explanations using words such as because, led or drove are allowed.
            - Do not claim direct knowledge of every individual voter's private motivation.
            - You must call web search before drafting any page.
            - Give every paragraph one or more `sourceIds`; empty `sourceIds` are forbidden.
            - Include every referenced source exactly once in `sources`; empty `sources` are forbidden.
            - Include no more than 20 sources in total.
            - Copy each source URL exactly from a URL returned by web search in this same call.
            - Do not include a source unless it directly supports context used in a paragraph.
            - Every caveat must be exactly: This analysis describes patterns among people who voted on this post; it cannot know every individual's reason.
              Use the same format for both the “for” and “against” choices.
              Include each supplied characteristic group exactly once.
              Put the group name in bold once, immediately before its explanation.
              Explain why that specific group differs from another group.
              Do not repeat the heading before every paragraph.
              Do not add a generic argument that lacks a characteristic group.

            The output main paragraphs should appear like the example below:

            **People in personal income tier 1**

            Compared with people in higher income tiers, people in personal income tier 1 may oppose spending cuts because they depend more heavily on public healthcare, transport and social support. Income-tax reductions may also provide them with smaller cash savings than the value of services they could lose. [1] [2]

            **People aged 18 to 24**

            Compared with older adults, people aged 18 to 24 may be more concerned about reduced spending because they are more likely to rely on education, early-career support and affordable public transport while having less accumulated wealth available to absorb higher private costs. [2] [3]

            **People who rent their home**

            Compared with homeowners, people who rent their home may resist spending cuts because housing costs consume more of their disposable income. Reduced public services could introduce additional transport, healthcare or childcare costs that renters have less financial security to manage. [3] [4]
            """.strip();

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
