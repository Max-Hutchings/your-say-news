package com.yoursay.votes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.yoursay.votes.PostAnalysisAggregateService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class PostAnalysisAggregateServiceIntegrationTest {
    @Inject
    PostAnalysisAggregateService aggregates;
    @Inject
    AgroalDataSource dataSource;
    @Inject
    ObjectMapper objectMapper;

    @Test
    void repeatedCapturesOfUnchangedDataKeepOneVersion() throws Exception {
        long postId = createPost();
        try {
            var first = aggregates.capture(postId);
            var second = aggregates.capture(postId);

            assertNotNull(first.capturedAt());
            assertNotNull(second.capturedAt());
            assertEquals(first.aggregateVersion(), second.aggregateVersion());

            insertIncomeVote(postId, 913L, "AGREE");
            var changed = aggregates.capture(postId);
            assertNotEquals(first.aggregateVersion(), changed.aggregateVersion());
        } finally {
            deletePost(postId);
        }
    }

    @Test
    void capturedIncomeCohortCarriesTheResolvedRealRangeForUnwrapped() throws Exception {
        long postId = createPost();
        try {
            insertIncomeVote(postId, 911L, "AGREE");
            insertIncomeVote(postId, 912L, "DISAGREE");

            var aggregate = aggregates.capture(postId);
            var cohort = aggregate.cohorts().stream()
                    .filter(value -> value.dimensions().size() == 1)
                    .filter(value -> value.dimensions().getFirst().axis()
                            .equals("personalIncomeRange"))
                    .findFirst().orElseThrow();
            var dimension = cohort.dimensions().getFirst();

            assertEquals("personalIncomeRange=income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
                    cohort.cohortId());
            assertEquals(2, cohort.sampleSize());
            assertEquals(100.0, cohort.populationSharePercentage());
            assertEquals("EXCLUSIVE", cohort.membershipSemantics().name());
            assertEquals(List.of(1L, 1L), cohort.options().stream()
                    .map(value -> value.count()).toList());
            assertEquals(List.of(50.0, 50.0), cohort.options().stream()
                    .map(value -> value.percentage()).toList());
            assertEquals("GBP 25k to GBP 40k", dimension.label());
            assertEquals("GB", dimension.income().marketCode());
            assertEquals("United Kingdom", dimension.income().marketLabel());
            assertEquals("GBP", dimension.income().currencyCode());
            assertEquals(25_000L, dimension.income().lowerInclusive());
            assertEquals(40_000L, dimension.income().upperExclusive());
            assertEquals("TIER_3", dimension.income().relativeTier());
            JsonNode aggregateJson = objectMapper.valueToTree(aggregate);
            Set<String> actualFields = new HashSet<>();
            collectFieldNames(aggregateJson, actualFields);
            assertTrue(actualFields.contains("suppressBelow"));
            assertTrue(AGGREGATE_ALLOWED_FIELDS.containsAll(actualFields),
                    () -> "Unexpected aggregate fields: " + difference(actualFields, AGGREGATE_ALLOWED_FIELDS));
        } finally {
            deletePost(postId);
        }
    }

    private long createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction
                     ) values (1, 'Stable summary', 'Stable question?',
                         false, now(), now(), 'BINARY', 'GLOBAL')
                     returning id
                     """)) {
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                options.executeUpdate();
            }
            return postId;
        }
    }

    private void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from post where id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }

    private void insertIncomeVote(long postId, long userId, String semanticKey) throws Exception {
        String snapshot = """
                {
                  "personalIncomeRange": "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
                  "personalIncome": {
                    "answerVersion": 2,
                    "profileId": "GB-GBP-GROSS-2025-v1",
                    "profileVersion": 1,
                    "marketCode": "GB",
                    "currencyCode": "GBP",
                    "measure": "PERSONAL",
                    "bandId": "PERSONAL_TIER_3",
                    "relativeTier": "TIER_3"
                  }
                }
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement vote = connection.prepareStatement("""
                        INSERT INTO votes (post_id, user_id, option_id, characteristic_snapshot)
                        SELECT ?, ?, id, ?::jsonb
                        FROM post_vote_option
                        WHERE post_id = ? AND semantic_key = ?
                        """)) {
            vote.setLong(1, postId);
            vote.setLong(2, userId);
            vote.setString(3, snapshot);
            vote.setLong(4, postId);
            vote.setString(5, semanticKey);
            assertEquals(1, vote.executeUpdate());
        }
    }

    private static final Set<String> AGGREGATE_ALLOWED_FIELDS = Set.of(
            "schemaVersion", "postId", "votingType", "summary", "question", "jurisdiction",
            "options", "canonicalVoteCount", "aggregateVersion", "capturedAt", "overall",
            "cohorts", "metadata", "id", "label", "ordinal", "semanticKey", "optionId",
            "count", "percentage", "cohortId", "dimensions", "membershipSemantics",
            "sampleSize", "populationSharePercentage", "axis", "bucket", "income",
            "bucketId", "contextLabel", "relativeLabel", "marketCode", "marketLabel",
            "currencyCode", "measure", "measureLabel", "lowerInclusive", "upperExclusive",
            "relativeTier", "profileId", "profileVersion", "bandId", "compositionPercentage",
            "differenceFromOverallPercentagePoints", "differenceFromRestPercentagePoints",
            "wilson95Low", "wilson95High", "rawPValue", "adjustedQValue", "statisticalTest",
            "ruleSetVersion", "suppressBelow", "minimumOverallSample",
            "minimumCohortSample", "minimumIntersectionSample",
            "minimumCohortSharePercentage", "minimumEffectPercentagePoints",
            "falseDiscoveryRate", "suppressedCohorts", "testedComparisons");

    private static void collectFieldNames(JsonNode node, Set<String> target) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                target.add(entry.getKey());
                collectFieldNames(entry.getValue(), target);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, target));
        }
    }

    private static Set<String> difference(Set<String> actual, Set<String> allowed) {
        Set<String> unexpected = new HashSet<>(actual);
        unexpected.removeAll(allowed);
        return unexpected;
    }
}
