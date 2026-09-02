package com.yoursay.posts;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the real posts → user wiring, with nothing mocked: the post read path runs on a reactive
 * Vert.x context, while the user domain's handle lookup is blocking Hibernate ORM. Every other
 * suite mocks {@code UserServiceClient}, so only this test would catch the blocking call being
 * made on the event loop, or the lookup being wired to the wrong field.
 */
@QuarkusTest
@TestSecurity(user = "reader@yoursay.com", roles = "user")
public class PostAuthorUsernameWiringTest {

    private static final String HANDLE = "nadia.reports";
    private static final String EMAIL = "nadia.wiring@example.com";

    @Inject
    AgroalDataSource dataSource;

    @Test
    public void readingAPostResolvesItsAuthorsHandleThroughTheRealUserDomain() throws Exception {
        long authorId = insertAuthor();
        long postId = insertPost(authorId);
        try {
            given()
                    .when().get("/posts/" + postId)
                    .then()
                    .statusCode(200)
                    .body("userId", is((int) authorId))
                    .body("authorUsername", is(HANDLE))
                    // The handle is the only author detail that crosses — never the PII beside it.
                    .body("authorEmail", org.hamcrest.Matchers.nullValue())
                    .body("authorDisplayName", org.hamcrest.Matchers.nullValue());
        } finally {
            deletePost(postId);
            deleteAuthor();
        }
    }

    private long insertAuthor() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into your_say_user(
                         email, first_name, last_name, display_name, handle, created_date, active)
                     values (?, 'Nadia', 'Rahman', 'Nadia Rahman', ?, now(), true)
                     returning id
                     """)) {
            statement.setString(1, EMAIL);
            statement.setString(2, HANDLE);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private long insertPost(long authorId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into post (user_id, summary, support_question, is_ai_generated,
                                       jurisdiction, voting_type, created_at, updated_at)
                     values (?, 'Rail funding was cut again this year.',
                             'Should the rail budget be restored?', false, 'GB', 'BINARY',
                             now(), now())
                     returning id
                     """)) {
            statement.setLong(1, authorId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("delete from post where id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }

    private void deleteAuthor() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from your_say_user where email = ?")) {
            statement.setString(1, EMAIL);
            statement.executeUpdate();
        }
    }
}
