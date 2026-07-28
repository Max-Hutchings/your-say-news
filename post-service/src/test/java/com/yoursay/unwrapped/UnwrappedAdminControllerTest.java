package com.yoursay.unwrapped;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class UnwrappedAdminControllerTest {
    @Inject
    AgroalDataSource dataSource;

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminCanForceOneIdempotentJobBelowTheAutomaticMilestone() throws Exception {
        long postId = createPost();
        try {
            String path = "/admin/unwrapped/posts/" + postId + "/generate";
            String firstJobId = given()
                    .when().post(path)
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) postId))
                    .body("milestone", equalTo(0))
                    .body("status", equalTo("PENDING"))
                    .body("created", equalTo(true))
                    .extract().path("jobId");

            String repeatedJobId = given()
                    .when().post(path)
                    .then()
                    .statusCode(202)
                    .body("postId", equalTo((int) postId))
                    .body("milestone", equalTo(0))
                    .body("status", equalTo("PENDING"))
                    .body("created", equalTo(false))
                    .extract().path("jobId");

            org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(firstJobId),
                    UUID.fromString(repeatedJobId));
            org.junit.jupiter.api.Assertions.assertEquals(1, jobCount(postId));
        } finally {
            deletePost(postId);
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void unknownPostDoesNotCreateAJob() {
        given()
                .when().post("/admin/unwrapped/posts/999999/generate")
                .then()
                .statusCode(404)
                .body("code", equalTo("UNWRAPPED_POST_NOT_FOUND"));
    }

    @Test
    @TestSecurity(user = "reader@yoursay.com", roles = "user")
    void nonAdminCannotForceGeneration() {
        given()
                .when().post("/admin/unwrapped/posts/1/generate")
                .then()
                .statusCode(403);
    }

    private long createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction
                     ) values (1, 'Forced generation summary', 'Should this be generated?',
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

    private int jobCount(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from unwrapped_analysis_job where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
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
}
