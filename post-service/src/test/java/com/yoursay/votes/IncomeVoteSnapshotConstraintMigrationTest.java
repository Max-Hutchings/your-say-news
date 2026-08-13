package com.yoursay.votes;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Proves vote persistence cannot reintroduce tier-only or inconsistent income snapshots. */
@QuarkusTest
class IncomeVoteSnapshotConstraintMigrationTest {

    private static final String SCHEMA = "income_vote_snapshot_constraint_test";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void removesHistoricalLegacyValuesAndRejectsNewInvalidIncomeSnapshots() throws Exception {
        try {
            createVoteFixture();
            applyMigration();
            assertHistoricalInvalidIncomeWasRemoved();
            assertValidVersionedIncomeCanBeInserted();
            assertLegacyIncomeCannotBeInserted();
            assertInconsistentStructuredIncomeCannotBeInserted();
        } finally {
            dropFixtureSchema();
        }
    }

    private void createVoteFixture() throws Exception {
        withFixtureStatement(statement -> {
            statement.execute("""
                    CREATE TABLE votes (
                      id BIGINT PRIMARY KEY,
                      characteristic_snapshot JSONB
                    )
                    """);
            statement.execute("""
                    INSERT INTO votes VALUES
                      (1, '{"personalIncomeRange":"LEGACY_BETWEEN_50K_AND_75K",\
                            "householdIncomeRange":"V2_TIER_7",\
                            "gender":"WOMAN"}')
                    """);
        });
    }

    private void applyMigration() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            setFixtureSearchPath(connection);
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(SCHEMA);
            database.setLiquibaseSchemaName(SCHEMA);
            Liquibase liquibase = new Liquibase(
                    "db/migrations/0017-enforce-versioned-income-vote-snapshots.xml",
                    new ClassLoaderResourceAccessor(), database);
            try {
                liquibase.update(new Contexts(), new LabelExpression());
            } finally {
                try {
                    resetSearchPath(connection);
                } finally {
                    liquibase.close();
                }
            }
        }
    }

    private void assertHistoricalInvalidIncomeWasRemoved() throws Exception {
        withFixtureStatement(statement -> assertValue(statement, """
                SELECT characteristic_snapshot = '{"gender":"WOMAN"}'::jsonb
                FROM votes WHERE id = 1
                """, true));
    }

    private void assertValidVersionedIncomeCanBeInserted() throws Exception {
        withFixtureStatement(statement -> assertEquals(1, statement.executeUpdate("""
                INSERT INTO votes VALUES (2, '{
                  "personalIncomeRange":
                    "income|GB-GBP-GROSS-2025-v1|PERSONAL|PERSONAL_TIER_3",
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
                }')
                """)));
    }

    private void assertLegacyIncomeCannotBeInserted() throws Exception {
        withFixtureStatement(statement -> assertThrows(SQLException.class,
                () -> statement.executeUpdate("""
                        INSERT INTO votes VALUES
                          (3, '{"personalIncomeRange":"V2_TIER_3"}')
                        """)));
    }

    private void assertInconsistentStructuredIncomeCannotBeInserted() throws Exception {
        withFixtureStatement(statement -> assertThrows(SQLException.class,
                () -> statement.executeUpdate("""
                        INSERT INTO votes VALUES (4, '{
                          "householdIncomeRange":
                            "income|GB-GBP-GROSS-2025-v1|HOUSEHOLD|HOUSEHOLD_TIER_7",
                          "householdIncome": {
                            "answerVersion": 2,
                            "profileId": "GB-GBP-GROSS-2025-v1",
                            "profileVersion": 1,
                            "marketCode": "US",
                            "currencyCode": "USD",
                            "measure": "HOUSEHOLD",
                            "bandId": "HOUSEHOLD_TIER_7",
                            "relativeTier": "TIER_7"
                          }
                        }')
                        """)));
    }

    private void withFixtureStatement(StatementAction action) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
            setFixtureSearchPath(connection);
            try {
                action.run(statement);
            } finally {
                resetSearchPath(connection);
            }
        }
    }

    private void dropFixtureSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    private static void assertValue(Statement statement, String sql, Object expected) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(expected, result.getObject(1));
        }
    }

    private static void setFixtureSearchPath(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + SCHEMA);
        }
    }

    private static void resetSearchPath(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("RESET search_path");
        }
    }

    @FunctionalInterface
    private interface StatementAction {
        void run(Statement statement) throws Exception;
    }
}
