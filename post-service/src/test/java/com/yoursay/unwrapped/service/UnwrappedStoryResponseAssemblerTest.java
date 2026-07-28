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
import com.yoursay.unwrapped.dto.UnwrappedClaimDraftV1;
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
    private static final UnwrappedSourceDraftV1 ONS_SOURCE = new UnwrappedSourceDraftV1(
            "ons-public-finances",
            "https://www.ons.gov.uk/economy/governmentpublicsectorandtaxes",
            "Office for National Statistics",
            "Public sector finances",
            SourceClassification.OFFICIAL);
    private static final UnwrappedSourceDraftV1 IFS_SOURCE = new UnwrappedSourceDraftV1(
            "ifs-tax-outlook",
            "https://ifs.org.uk/publications/tax-outlook",
            "Institute for Fiscal Studies",
            "Tax outlook",
            SourceClassification.ACADEMIC);
    private static final UnwrappedSourceDraftV1 UNUSED_SOURCE = new UnwrappedSourceDraftV1(
            "unused-source",
            "https://www.oecd.org/tax/tax-policy/",
            "OECD",
            "Tax policy analysis",
            SourceClassification.OFFICIAL);

    @Test
    void resolvesOnlyEachArgumentsCitedSourcesInFirstCitationOrder() {
        UnwrappedArgumentDraftV1 lowerTaxArgument = argument(71L, List.of(
                claim("claim-tax-burden", List.of(IFS_SOURCE.id(), IFS_SOURCE.id())),
                claim("claim-public-finances", List.of(ONS_SOURCE.id()))));
        UnwrappedArgumentDraftV1 servicesArgument = argument(72L, List.of(
                claim("claim-service-funding", List.of(ONS_SOURCE.id()))));
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(lowerTaxArgument, servicesArgument),
                List.of(ONS_SOURCE, IFS_SOURCE, UNUSED_SOURCE));

        List<UnwrappedArgumentPageDto> pages =
                UnwrappedStoryResponseAssembler.argumentPages(draft);

        assertEquals(List.of(
                expectedPage(lowerTaxArgument, List.of(IFS_SOURCE, ONS_SOURCE)),
                expectedPage(servicesArgument, List.of(ONS_SOURCE))
        ), pages);
    }

    @Test
    void rejectsAStoredArgumentWithADanglingSourceReference() {
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(argument(71L, List.of(claim(
                        "claim-missing-source", List.of("missing-source"))))),
                List.of(ONS_SOURCE));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> UnwrappedStoryResponseAssembler.argumentPages(draft));

        assertEquals(
                "Stored Unwrapped story references unknown source id: missing-source",
                error.getMessage());
    }

    @Test
    void rejectsDuplicateSourceIdsInsteadOfChoosingArbitraryMetadata() {
        UnwrappedSourceDraftV1 duplicate = new UnwrappedSourceDraftV1(
                ONS_SOURCE.id(),
                "https://www.ons.gov.uk/economy/inflationandpriceindices",
                "Office for National Statistics",
                "Consumer price inflation",
                SourceClassification.OFFICIAL);
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(argument(71L, List.of(claim(
                        "claim-public-finances", List.of(ONS_SOURCE.id()))))),
                List.of(ONS_SOURCE, duplicate));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> UnwrappedStoryResponseAssembler.argumentPages(draft));

        assertEquals(
                "Stored Unwrapped story has duplicate source id: ons-public-finances",
                error.getMessage());
    }

    @Test
    void voterStoryJsonHasOnlyPageScopedSourcesAndNoIdentityFields() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UnwrappedResearchDraftV1 draft = new UnwrappedResearchDraftV1(
                List.of(
                        argument(71L, List.of(
                                claim("claim-tax-burden", List.of(IFS_SOURCE.id())),
                                claim("claim-public-finances", List.of(ONS_SOURCE.id())))),
                        argument(72L, List.of(
                                claim("claim-service-funding", List.of(ONS_SOURCE.id()))))
                ),
                List.of(ONS_SOURCE, IFS_SOURCE, UNUSED_SOURCE));
        UnwrappedAnalysisJob job =
                new UnwrappedAnalysisJob(44L, 100, "unwrapped-analysis-v1");
        job.attachAggregate(128L, "sha256:public-contract", objectMapper.createObjectNode());
        UnwrappedStory story =
                new UnwrappedStory(job, objectMapper.valueToTree(draft), "configured-model");
        PostVotingConfigurationService posts = mock(PostVotingConfigurationService.class);
        when(posts.findByPostId(44L)).thenReturn(Optional.of(new PostVotingConfigurationDto(
                44L,
                VotingType.BINARY,
                List.of(
                        new VoteOptionDto(71L, "Reduce public spending", 0, null),
                        new VoteOptionDto(72L, "Keep public services funded", 1, null)))));
        UnwrappedServiceImpl service = new UnwrappedServiceImpl();
        service.objectMapper = objectMapper;
        service.postService = posts;

        UnwrappedStoryDto response = service.toStory(story);
        JsonNode json = objectMapper.valueToTree(response);
        JsonNode firstPage = json.path("argumentPages").get(0);
        JsonNode firstClaim = firstPage.path("contextClaims").get(0);
        JsonNode firstSource = firstPage.path("sources").get(0);

        assertEquals(Set.of(
                "schemaVersion", "storyId", "postId", "milestone", "canonicalVoteCount",
                "aggregateVersion", "generatedAt", "model", "argumentPages",
                "reconsiderationQuestion", "reconsiderationOptions"
        ), fieldNames(json));
        assertEquals(Set.of(
                "optionId", "headline", "usedCohortIds", "contextClaims",
                "synthesis", "caveat", "sources"
        ), fieldNames(firstPage));
        assertEquals(
                Set.of("id", "statement", "sourceIds", "interpretation"),
                fieldNames(firstClaim));
        assertEquals(
                Set.of("id", "url", "publisher", "title", "classification"),
                fieldNames(firstSource));
        assertEquals("unwrapped-story-v1", json.path("schemaVersion").asText());
        assertEquals(story.getId().toString(), json.path("storyId").asText());
        assertEquals(44L, json.path("postId").asLong());
        assertEquals(100, json.path("milestone").asInt());
        assertEquals(128L, json.path("canonicalVoteCount").asLong());
        assertEquals("sha256:public-contract", json.path("aggregateVersion").asText());
        assertEquals("configured-model", json.path("model").asText());
        assertEquals(2, json.path("argumentPages").size());
        assertEquals(71L, firstPage.path("optionId").asLong());
        assertEquals(
                "A responsible case for reducing taxation",
                firstPage.path("headline").asText());
        assertEquals(
                List.of("ageRange=AGE_25_34"),
                stringValues(firstPage.path("usedCohortIds")));
        assertEquals("claim-tax-burden", firstClaim.path("id").asText());
        assertEquals(
                "Published evidence provides relevant context for this policy choice.",
                firstClaim.path("statement").asText());
        assertEquals(List.of(IFS_SOURCE.id()), stringValues(firstClaim.path("sourceIds")));
        assertFalse(firstClaim.path("interpretation").asBoolean());
        assertEquals(
                "The evidence supports a careful assessment of reducing taxation.",
                firstPage.path("synthesis").asText());
        assertEquals(
                "The reducing taxation association among voters on this post does not prove individual motivation.",
                firstPage.path("caveat").asText());
        assertEquals(objectMapper.valueToTree(IFS_SOURCE), firstSource);
        assertEquals(
                List.of(IFS_SOURCE.id(), ONS_SOURCE.id()),
                sourceIds(firstPage));
        assertEquals(
                List.of(ONS_SOURCE.id()),
                sourceIds(json.path("argumentPages").get(1)));
        assertFalse(json.toString().contains(UNUSED_SOURCE.id()));
        assertTrue(json.findValues("email").isEmpty());
        assertTrue(json.findValues("userId").isEmpty());
        assertTrue(json.findValues("dateOfBirth").isEmpty());
        assertTrue(json.findValues("name").isEmpty());
        assertTrue(json.findValues("firstName").isEmpty());
        assertTrue(json.findValues("lastName").isEmpty());
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

    private static UnwrappedArgumentPageDto expectedPage(
            UnwrappedArgumentDraftV1 argument,
            List<UnwrappedSourceDraftV1> sources
    ) {
        return new UnwrappedArgumentPageDto(
                argument.optionId(),
                argument.headline(),
                argument.usedCohortIds(),
                argument.contextClaims(),
                argument.synthesis(),
                argument.caveat(),
                sources);
    }

    private static UnwrappedArgumentDraftV1 argument(
            Long optionId,
            List<UnwrappedClaimDraftV1> claims
    ) {
        String subject = optionId.equals(71L) ? "reducing taxation" : "funding public services";
        String cohort = optionId.equals(71L)
                ? "ageRange=AGE_25_34"
                : "region=GREATER_LONDON";
        return new UnwrappedArgumentDraftV1(
                optionId,
                "A responsible case for " + subject,
                List.of(cohort),
                claims,
                "The evidence supports a careful assessment of " + subject + ".",
                "The " + subject
                        + " association among voters on this post does not prove individual motivation.");
    }

    private static UnwrappedClaimDraftV1 claim(String id, List<String> sourceIds) {
        return new UnwrappedClaimDraftV1(
                id,
                "Published evidence provides relevant context for this policy choice.",
                sourceIds,
                false);
    }
}
