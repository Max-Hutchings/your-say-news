package com.yoursay.unwrapped.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.posts.PostVotingConfigurationService;
import com.yoursay.posts.VotingType;
import com.yoursay.posts.dto.PostVotingConfigurationDto;
import com.yoursay.posts.dto.VoteOptionDto;
import com.yoursay.unwrapped.SourceClassification;
import com.yoursay.unwrapped.dto.UnwrappedArgumentDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedArgumentPageDto;
import com.yoursay.unwrapped.dto.UnwrappedArticleParagraphDraftV2;
import com.yoursay.unwrapped.dto.UnwrappedResearchDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedSourceDraftV1;
import com.yoursay.unwrapped.dto.UnwrappedStoryDto;
import com.yoursay.unwrapped.model.UnwrappedAnalysisJob;
import com.yoursay.unwrapped.model.UnwrappedStory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnwrappedStoryResponseAssemblerTest {
    private static final UnwrappedSourceDraftV1 ONS = source(
            "ons-public-finances", "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes",
            "Office for National Statistics", "Public sector finances",
            SourceClassification.OFFICIAL);
    private static final UnwrappedSourceDraftV1 IFS = source(
            "ifs-tax-outlook", "https://ifs.org.uk/publications/tax-outlook",
            "Institute for Fiscal Studies", "Tax outlook", SourceClassification.ACADEMIC);
    private static final UnwrappedSourceDraftV1 UNUSED = source(
            "unused-source", "https://www.oecd.org/tax/tax-policy/",
            "OECD", "Tax policy analysis", SourceClassification.OFFICIAL);

    @Test
    void resolvesOnlyEachArticlesCitedSourcesInFirstCitationOrder() {
        UnwrappedArgumentDraftV1 first = argument(71L, "25–34-year-olds",
                List.of(paragraph("First paragraph.", IFS.id(), IFS.id()),
                        paragraph("Second paragraph.", ONS.id())));
        UnwrappedArgumentDraftV1 second = argument(72L, "Public-sector workers",
                List.of(paragraph("First paragraph.", ONS.id()),
                        paragraph("Second paragraph.", ONS.id())));

        List<UnwrappedArgumentPageDto> pages = UnwrappedStoryResponseAssembler.argumentPages(
                new UnwrappedResearchDraftV1(List.of(first, second), List.of(ONS, IFS, UNUSED)));

        assertEquals(List.of(IFS, ONS), pages.getFirst().sources());
        assertEquals(List.of(ONS), pages.getLast().sources());
        assertEquals(first.paragraphs(), pages.getFirst().paragraphs());
        assertEquals(first.selectedCohortIds(), pages.getFirst().selectedCohortIds());
    }

    @Test
    void rejectsDanglingAndDuplicateSourceReferences() {
        UnwrappedResearchDraftV1 dangling = new UnwrappedResearchDraftV1(
                List.of(argument(71L, "25–34-year-olds", List.of(
                        paragraph("First paragraph.", "missing-source"),
                        paragraph("Second paragraph.", ONS.id())))), List.of(ONS));
        assertEquals("Stored Unwrapped story references unknown source id: missing-source",
                assertThrows(IllegalStateException.class,
                        () -> UnwrappedStoryResponseAssembler.argumentPages(dangling)).getMessage());

        UnwrappedSourceDraftV1 duplicate = source(ONS.id(),
                "https://www.ons.gov.uk/economy/inflationandpriceindices",
                "Office for National Statistics", "Consumer price inflation",
                SourceClassification.OFFICIAL);
        UnwrappedResearchDraftV1 duplicated = new UnwrappedResearchDraftV1(
                List.of(argument(71L, "25–34-year-olds", List.of(
                        paragraph("First paragraph.", ONS.id()),
                        paragraph("Second paragraph.", ONS.id())))), List.of(ONS, duplicate));
        assertEquals("Stored Unwrapped story has duplicate source id: ons-public-finances",
                assertThrows(IllegalStateException.class,
                        () -> UnwrappedStoryResponseAssembler.argumentPages(duplicated)).getMessage());
    }

    @Test
    void voterStoryJsonUsesTheV2UnifiedArticleContractWithoutIdentityFields() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(List.of(
                argument(71L, "25–34-year-olds", List.of(
                        paragraph("Tax pressure explains why this cohort favours lower deductions.", IFS.id()),
                        paragraph("Official figures provide the wider financial context.", ONS.id()))),
                argument(72L, "Public-sector workers", List.of(
                        paragraph("Service exposure explains why this cohort protects funding.", ONS.id()),
                        paragraph("Official figures show what reductions could put at risk.", ONS.id())))),
                List.of(ONS, IFS, UNUSED));
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(44L, 100, UnwrappedStory.ANALYSIS_VERSION);
        job.attachAggregate(128L, "sha256:public-contract", objectMapper.createObjectNode());
        UnwrappedStory story = new UnwrappedStory(job, objectMapper.valueToTree(draft), "configured-model");
        PostVotingConfigurationService posts = mock(PostVotingConfigurationService.class);
        when(posts.findByPostId(44L)).thenReturn(Optional.of(new PostVotingConfigurationDto(
                44L, VotingType.BINARY, List.of(
                new VoteOptionDto(71L, "Reduce public spending", 0, null),
                new VoteOptionDto(72L, "Keep public services funded", 1, null)))));
        UnwrappedServiceImpl service = new UnwrappedServiceImpl();
        service.objectMapper = objectMapper;
        service.postService = posts;

        UnwrappedStoryDto response = service.toStory(story);
        JsonNode json = objectMapper.valueToTree(response);
        JsonNode firstPage = json.path("argumentPages").get(0);
        JsonNode firstParagraph = firstPage.path("paragraphs").get(0);

        assertEquals("unwrapped-story-v2", json.path("schemaVersion").asText());
        assertEquals(Set.of("optionId", "headline", "selectedCohortIds", "paragraphs",
                "caveat", "sources"), fieldNames(firstPage));
        assertEquals(Set.of("text", "sourceIds"), fieldNames(firstParagraph));
        assertEquals(List.of("ageRange=AGE_25_34"),
                stringValues(firstPage.path("selectedCohortIds")));
        assertEquals("Tax pressure explains why this cohort favours lower deductions.",
                firstParagraph.path("text").asText());
        assertEquals(List.of(IFS.id()), stringValues(firstParagraph.path("sourceIds")));
        assertEquals(List.of(IFS.id(), ONS.id()), sourceIds(firstPage));
        assertFalse(json.toString().contains(UNUSED.id()));
        assertTrue(json.findValues("email").isEmpty());
        assertTrue(json.findValues("userId").isEmpty());
        assertTrue(json.findValues("dateOfBirth").isEmpty());
        assertTrue(json.findValues("firstName").isEmpty());
        assertTrue(json.findValues("lastName").isEmpty());
    }

    @Test
    void adaptsStoredV1DraftsIntoTheUnifiedParagraphPresentation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode legacy = objectMapper.readTree("""
                {
                  "pages": [{
                    "optionId": 71,
                    "headline": "Legacy headline",
                    "usedCohortIds": ["ageRange=AGE_25_34"],
                    "contextClaims": [
                      {"statement": "First sourced fact.", "sourceIds": ["s1"]},
                      {"statement": "Second sourced fact.", "sourceIds": ["s2"]}
                    ],
                    "synthesis": "Legacy persuasive synthesis.",
                    "caveat": "Legacy limitation."
                  }],
                  "sources": [
                    {"id":"s1","url":"https://www.ons.gov.uk/a","publisher":"ONS","title":"A source","classification":"OFFICIAL"},
                    {"id":"s2","url":"https://www.ons.gov.uk/b","publisher":"ONS","title":"B source","classification":"OFFICIAL"}
                  ]
                }
                """);

        UnwrappedResearchDraftV1 adapted = LegacyUnwrappedDraftAdapter.convert(legacy, objectMapper);

        assertEquals(List.of("First sourced fact.", "Second sourced fact.",
                        "Legacy persuasive synthesis."),
                adapted.pages().getFirst().paragraphs().stream()
                        .map(UnwrappedArticleParagraphDraftV2::text).toList());
        assertEquals(List.of("s1", "s2"),
                adapted.pages().getFirst().paragraphs().getLast().sourceIds());
    }

    private static UnwrappedArgumentDraftV1 argument(
            long optionId, String cohort, List<UnwrappedArticleParagraphDraftV2> paragraphs) {
        String cohortId = optionId == 71L ? "ageRange=AGE_25_34" : "occupation=PUBLIC_SECTOR";
        return new UnwrappedArgumentDraftV1(optionId,
                "Why " + cohort + " favour this policy change",
                List.of(cohortId), paragraphs,
                "This analysis describes patterns among people who voted on this post; "
                        + "it cannot know every individual's reason.");
    }

    private static UnwrappedArticleParagraphDraftV2 paragraph(String text, String... ids) {
        return new UnwrappedArticleParagraphDraftV2(text, List.of(ids));
    }

    private static UnwrappedSourceDraftV1 source(String id, String url, String publisher,
                                                  String title, SourceClassification classification) {
        return new UnwrappedSourceDraftV1(id, url, publisher, title, classification);
    }

    private static List<String> sourceIds(JsonNode argumentPage) {
        return argumentPage.path("sources").valueStream()
                .map(source -> source.path("id").asText()).toList();
    }

    private static List<String> stringValues(JsonNode array) {
        return array.valueStream().map(JsonNode::asText).toList();
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new TreeSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }
}
