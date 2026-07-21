package com.yoursay.user.model;

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
    void everySeededPostAuthorIsOfficialAndNonAuthorsRemainStandard() throws Exception {
        Map<Long, String> actual = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select id, account_type, publisher_status
                     from your_say_user
                     where id between 1 and 10
                     order by id
                     """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                actual.put(result.getLong("id"),
                        result.getString("account_type") + "/" + result.getString("publisher_status"));
            }
        }

        assertEquals(Map.of(
                1L, "OFFICIAL/ACTIVE",
                2L, "OFFICIAL/ACTIVE",
                3L, "STANDARD/NONE",
                4L, "OFFICIAL/ACTIVE",
                5L, "STANDARD/NONE",
                6L, "STANDARD/NONE",
                7L, "OFFICIAL/ACTIVE",
                8L, "OFFICIAL/ACTIVE",
                9L, "STANDARD/NONE",
                10L, "STANDARD/NONE"
        ), actual);
    }

    @Test
    void databaseRejectsPublisherStatusOnAStandardAccount() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    update your_say_user
                    set publisher_status = 'ACTIVE'
                    where id = 5
                    """)) {
                assertThrows(SQLException.class, statement::executeUpdate);
            } finally {
                connection.rollback();
            }
        }
    }
}
