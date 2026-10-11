package com.yoursay.user.user.model;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AccountPublishingSeedTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    void seededAccountsCoverAdminOfficialUserAndInactivePaths() throws Exception {
        Map<Long, String> actual = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id, account_type, publishing_enabled, active
                     from your_say_user
                     where id between 1 and 11
                     order by id
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                actual.put(result.getLong("id"),
                        result.getString("account_type") + "/" + result.getBoolean("publishing_enabled")
                                + "/" + result.getBoolean("active"));
            }
        }

        assertEquals(Map.ofEntries(
                Map.entry(1L, "OFFICIAL/true/true"),
                Map.entry(2L, "OFFICIAL/true/true"),
                Map.entry(3L, "USER/false/false"),
                Map.entry(4L, "OFFICIAL/true/true"),
                Map.entry(5L, "USER/false/true"),
                Map.entry(6L, "USER/false/true"),
                Map.entry(7L, "OFFICIAL/true/true"),
                Map.entry(8L, "OFFICIAL/true/true"),
                Map.entry(9L, "USER/false/true"),
                Map.entry(10L, "USER/false/true"),
                Map.entry(11L, "ADMIN/false/true")
        ), actual);
    }

    @Test
    void databaseRejectsPublishingOnAUserAccount() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    update your_say_user
                    set publishing_enabled = true
                    where id = 5
                    """)) {
                SQLException exception = assertThrows(SQLException.class, statement::executeUpdate);
                assertEquals("23514", exception.getSQLState());
                assertTrue(exception.getMessage().contains("ck_your_say_user_user_cannot_publish"));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void databaseAllowsPublishingOnAnAdminAccount() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    update your_say_user
                    set publishing_enabled = true
                    where id = 11 and account_type = 'ADMIN'
                    """)) {
                assertEquals(1, statement.executeUpdate());
            } finally {
                connection.rollback();
            }
        }
    }
}
