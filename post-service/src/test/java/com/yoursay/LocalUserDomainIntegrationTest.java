package com.yoursay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursay.user.usercharacteristic.model.Enums.AgeRange;
import com.yoursay.votes.CharacteristicSnapshot;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Year;
import java.util.HashSet;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestSecurity(user = "john.doe@example.com", roles = "user")
class LocalUserDomainIntegrationTest {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void postCreationAndVoteSnapshotUseTheLocalUserDomainsAndDatabase() throws Exception {
        Integer postId = null;
        String agentJobId = null;
        try {
            Response created = given()
                    .contentType("application/json")
                    .body("""
                            {
                              "summary": "Integration proof for the combined service.",
                              "supportQuestion": "Should local user-domain wiring replace the network call?",
                              "media": []
                            }
                            """)
                    .when().post("/posts")
                    .then()
                    .statusCode(201)
                    .body("userId", is(1))
                    .body("summary", is("Integration proof for the combined service."))
                    .body("voteOptions.size()", is(2))
                    .extract().response();

            postId = created.path("id");
            Integer agreeOptionId = created.path("voteOptions.find { it.semanticKey == 'AGREE' }.id");

            given()
                    .when().get("/feed?size=50")
                    .then()
                    .statusCode(200)
                    .body("find { it.id == %d }.userId".formatted(postId), is(1));

            Response vote = given()
                    .contentType("application/json")
                    .body("{ \"postId\": %d, \"optionId\": %d }".formatted(postId, agreeOptionId))
                    .when().post("/votes")
                    .then()
                    .statusCode(201)
                    .body("postId", is(postId))
                    .body("optionId", is(agreeOptionId))
                    .extract().response();
            assertEquals(Set.of("id", "postId", "optionId"), vote.jsonPath().getMap("$").keySet());

            assertStoredVoteUsesJohnsLocalIdentityAndCharacteristics(postId);

            agentJobId = given()
                    .contentType("application/json")
                    .body("{ \"request\": \"Summarise both sides of the local-domain migration.\" }")
                    .when().post("/agent/jobs")
                    .then()
                    .statusCode(202)
                    .body("status", is("PENDING"))
                    .extract().path("id");
            assertAgentJobUsesJohnsLocalIdentity(agentJobId);
        } finally {
            if (agentJobId != null) {
                deleteAgentJob(agentJobId);
            }
            if (postId != null) {
                deletePost(postId);
            }
        }
    }

    @Test
    @TestSecurity(user = "riley.reader@example.com", roles = "user")
    void aSecondLocalIdentityIsNotMistakenForJohnsPublishingAccount() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "summary": "A standard reader must not publish.",
                          "supportQuestion": "Should this request be rejected?",
                          "media": []
                        }
                        """)
                .when().post("/posts")
                .then()
                .statusCode(403)
                .body("code", is("POST_PUBLISHING_FORBIDDEN"));

        given()
                .contentType("application/json")
                .body("{ \"request\": \"This standard reader must not start an agent job.\" }")
                .when().post("/agent/jobs")
                .then()
                .statusCode(403)
                .body("code", is("AGENT_PUBLISHING_FORBIDDEN"));
    }

    private void assertStoredVoteUsesJohnsLocalIdentityAndCharacteristics(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select user_id, characteristic_snapshot
                       from votes
                      where post_id = ?
                     """)) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1L, result.getLong("user_id"));
                JsonNode snapshot = objectMapper.readTree(result.getString("characteristic_snapshot"));
                String ageRange = AgeRange.fromAge(Year.now().getValue() - 1996).name();
                JsonNode expected = objectMapper.readTree("""
                        {
                          "politicalPersuasion": "CENTRE_LEFT",
                          "ageRange": "%s",
                          "gender": "MAN",
                          "sexAtBirth": "MALE",
                          "sexualOrientation": "STRAIGHT_HETEROSEXUAL",
                          "maritalStatus": "SINGLE",
                          "race": "WHITE_EUROPEAN",
                          "country": "United Kingdom",
                          "region": null,
                          "urbanRural": "URBAN",
                          "ukCounty": "BRISTOL",
                          "countryOfBirth": "UNITED_KINGDOM",
                          "citizenship": "BRITISH",
                          "religion": "NO_RELIGION",
                          "religiosity": "NOT_RELIGIOUS",
                          "education": "BACHELORS",
                          "occupation": "EMPLOYED_FULL_TIME",
                          "employmentSector": "IT_TECHNOLOGY",
                          "universitySubject": "COMPUTER_SCIENCE",
                          "personalIncomeRange": "BETWEEN_50K_AND_75K",
                          "householdIncomeRange": "BETWEEN_100K_AND_150K",
                          "height": "FEET_5_10_TO_6_0",
                          "weightRange": "KG_80_89",
                          "eyeColor": "BROWN",
                          "parent": "NOT_PARENT_CAREGIVER",
                          "newsFrequency": "8",
                          "hasPet": "true",
                          "petType": "DOG",
                          "chronotype": "NIGHT_OWL",
                          "outlook": "OPTIMIST",
                          "neurodivergent": "false",
                          "neurodivergenceType": null,
                          "hasDisability": "false",
                          "disabilityType": null,
                          "housingStatus": "OWN_MORTGAGE",
                          "propertyType": "FLAT_APARTMENT"
                        }
                        """.formatted(ageRange));
                assertEquals(expected, snapshot);
                Set<String> keys = new HashSet<>();
                snapshot.fieldNames().forEachRemaining(keys::add);
                assertEquals(CharacteristicSnapshot.AXES, keys);
                assertFalse(snapshot.has("userId"));
                assertFalse(snapshot.has("email"));
                assertFalse(snapshot.has("city"));
                assertFalse(snapshot.has("age"));
                assertFalse(snapshot.has("dateOfBirth"));
                assertFalse(result.next());
            }
        }
    }

    private void assertAgentJobUsesJohnsLocalIdentity(String jobId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select user_id from agent_generation_job where id = cast(? as uuid)")) {
            statement.setString(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(1L, result.getLong("user_id"));
                assertFalse(result.next());
            }
        }
    }

    private void deleteAgentJob(String jobId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from agent_generation_job where id = cast(? as uuid)")) {
            statement.setString(1, jobId);
            statement.executeUpdate();
        }
    }

    private void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement votes = connection.prepareStatement("delete from votes where post_id = ?")) {
                votes.setLong(1, postId);
                votes.executeUpdate();
            }
            try (PreparedStatement post = connection.prepareStatement("delete from post where id = ?")) {
                post.setLong(1, postId);
                post.executeUpdate();
            }
        }
    }
}
