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

@QuarkusTest
class AccountPublishingSeedTest {

    @Inject
    AgroalDataSource dataSource;

    @Test
    void seededAccountsCoverAdminOfficialUserAndInactivePaths() throws Exception {
        Map<Long, String> actual = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id, account_type, publisher_status, active
                     from your_say_user
                     where id between 1 and 11
                     order by id
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                actual.put(result.getLong("id"),
                        result.getString("account_type") + "/" + result.getString("publisher_status")
                                + "/" + result.getBoolean("active"));
            }
        }

        assertEquals(Map.ofEntries(
                Map.entry(1L, "OFFICIAL/ACTIVE/true"),
                Map.entry(2L, "OFFICIAL/ACTIVE/true"),
                Map.entry(3L, "USER/NONE/false"),
                Map.entry(4L, "OFFICIAL/ACTIVE/true"),
                Map.entry(5L, "USER/NONE/true"),
                Map.entry(6L, "USER/NONE/true"),
                Map.entry(7L, "OFFICIAL/ACTIVE/true"),
                Map.entry(8L, "OFFICIAL/ACTIVE/true"),
                Map.entry(9L, "USER/NONE/true"),
                Map.entry(10L, "USER/NONE/true"),
                Map.entry(11L, "ADMIN/NONE/true")
        ), actual);
    }

    @Test
    void databaseRejectsPublisherStatusOnANonOfficialAccount() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    update your_say_user
                    set publisher_status = 'ACTIVE'
                    where id = 5
                    """)) {
                SQLException exception = assertThrows(SQLException.class, statement::executeUpdate);
                assertEquals("23514", exception.getSQLState());
                org.junit.jupiter.api.Assertions.assertTrue(
                        exception.getMessage().contains("ck_your_say_user_non_official_cannot_publish"));
            } finally {
                connection.rollback();
            }
        }
    }
}
