package com.yoursay.user.usercharacteristic;

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
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Proves migration 0013 preserves representative answers from the pre-reform schema. */
@QuarkusTest
class CharacteristicMigrationTest {

    private static final String SCHEMA = "characteristic_migration_test";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void migratesExistingSingleValueAnswersBeforeDroppingOldColumns() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            try {
                int databaseYear;
                try (ResultSet result = statement.executeQuery("SELECT EXTRACT(YEAR FROM CURRENT_DATE)::int")) {
                    result.next();
                    databaseYear = result.getInt(1);
                }
                statement.execute("""
                        CREATE TABLE user_characteristic (
                          id BIGINT PRIMARY KEY,
                          parent VARCHAR(16),
                          property_type VARCHAR(16),
                          eye_color VARCHAR(16),
                          age_range VARCHAR(32),
                          citizenship VARCHAR(64),
                          pet_type VARCHAR(16),
                          neurodivergence_type VARCHAR(32),
                          disability_type VARCHAR(32),
                          country_of_birth VARCHAR(64),
                          height VARCHAR(32)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE user_characteristic_race (
                          user_characteristic_id BIGINT NOT NULL,
                          race VARCHAR(32) NOT NULL
                        )
                        """);
                statement.execute("""
                        INSERT INTO user_characteristic
                          (id, parent, property_type, eye_color, age_range, citizenship, pet_type,
                           neurodivergence_type, disability_type, country_of_birth, height)
                        VALUES
                          (42, 'NO', 'FLAT', 'BLUE', 'AGE_18_24', 'KOREA_NORTH', 'DOG',
                           'ADHD', 'HEARING', 'KOREA_SOUTH', 'FEET_5_0_TO_5_3')
                        """);
                statement.execute("INSERT INTO user_characteristic_race VALUES (42, 'ASIAN')");

                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName(SCHEMA);
                database.setLiquibaseSchemaName(SCHEMA);
                Liquibase liquibase = new Liquibase(
                        "db/user-migrations/0013-characteristics-reform.yaml",
                        new ClassLoaderResourceAccessor(),
                        database);
                try {
                    liquibase.update(new Contexts(), new LabelExpression());
                } finally {
                    try {
                        resetSearchPath(connection);
                    } finally {
                        liquibase.close();
                    }
                }

                try (Connection verificationConnection = dataSource.getConnection();
                        Statement verification = verificationConnection.createStatement()) {
                    verification.execute("SET search_path TO " + SCHEMA);
                    try {
                        assertSingleValue(verification,
                                "SELECT birth_year FROM user_characteristic WHERE id = 42",
                                databaseYear - 21);
                        assertSingleValue(verification,
                                "SELECT citizenship FROM user_characteristic_citizenship WHERE user_characteristic_id = 42",
                                "NORTH_KOREA");
                        assertSingleValue(verification,
                                "SELECT pet_type FROM user_characteristic_pet_type WHERE user_characteristic_id = 42",
                                "DOG");
                        assertSingleValue(verification,
                                "SELECT neurodivergence_type FROM user_characteristic_neurodivergence_type WHERE user_characteristic_id = 42",
                                "ADHD");
                        assertSingleValue(verification,
                                "SELECT disability_type FROM user_characteristic_disability_type WHERE user_characteristic_id = 42",
                                "HEARING");
                        assertSingleValue(verification,
                                "SELECT race FROM user_characteristic_race WHERE user_characteristic_id = 42",
                                "SOUTH_ASIAN");
                        assertSingleValue(verification,
                                "SELECT country_of_birth FROM user_characteristic WHERE id = 42",
                                "SOUTH_KOREA");
                        assertSingleValue(verification,
                                "SELECT height FROM user_characteristic WHERE id = 42",
                                "FEET_5_1_TO_5_3");
                        assertSingleValue(verification, """
                                SELECT COUNT(*) FROM information_schema.columns
                                WHERE table_schema = 'characteristic_migration_test'
                                  AND table_name = 'user_characteristic'
                                  AND column_name IN (
                                    'age_range', 'citizenship', 'pet_type',
                                    'neurodivergence_type', 'disability_type')
                                """, 0L);
                    } finally {
                        verification.execute("RESET search_path");
                    }
                }
            } finally {
                if (!connection.isClosed()) {
                    resetSearchPath(connection);
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    private static void assertSingleValue(Statement statement, String sql, Object expected) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(expected, result.getObject(1));
            assertFalse(result.next(), "Expected the query to return exactly one row");
        }
    }

    private static void resetSearchPath(Connection connection) throws Exception {
        try (Statement reset = connection.createStatement()) {
            reset.execute("RESET search_path");
        }
    }
}
