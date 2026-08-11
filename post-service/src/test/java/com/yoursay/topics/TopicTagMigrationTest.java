package com.yoursay.topics;

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

/** Proves migration 0016 preserves creator topic relationships from the old schema. */
@QuarkusTest
class TopicTagMigrationTest {

    private static final String SCHEMA = "topic_tag_migration_test";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void migratesExistingPostTopicsIntoAssignmentHistoryAndTheEffectiveProjection() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
            statement.execute("SET search_path TO " + SCHEMA);
            try {
                createOldSchema(statement);

                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName(SCHEMA);
                database.setLiquibaseSchemaName(SCHEMA);
                Liquibase liquibase = new Liquibase(
                        "db/migrations/0016-evolve-topics-to-topic-tags.xml",
                        new ClassLoaderResourceAccessor(), database);
                try {
                    liquibase.update(new Contexts(), new LabelExpression());
                } finally {
                    resetSearchPath(connection);
                    liquibase.close();
                }

                try (Connection verificationConnection = dataSource.getConnection();
                     Statement verification = verificationConnection.createStatement()) {
                    verification.execute("SET search_path TO " + SCHEMA);
                    try {
                        assertValue(verification, """
                                select topic_tag_id || '|' || source || '|' || review_state
                                from post_topic_tag_assignment where post_id = 42
                                """, "housing|CREATOR|ACCEPTED");
                        assertValue(verification, """
                                select topic_tag_id || '|' || effective_source
                                from effective_post_topic_tag where post_id = 42
                                """, "housing|CREATOR");
                        assertValue(verification, """
                                select count(*) from information_schema.tables
                                where table_schema = 'topic_tag_migration_test'
                                  and table_name = 'post_topic'
                                """, 0L);
                    } finally {
                        // The pool hands this connection to later tests; leaving search_path on a
                        // schema this test then drops makes every subsequent query fail.
                        verification.execute("RESET search_path");
                    }
                }
            } finally {
                if (!connection.isClosed()) {
                    resetSearchPath(connection);
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    private static void createOldSchema(Statement statement) throws Exception {
        statement.execute("CREATE TABLE post (id BIGINT PRIMARY KEY)");
        statement.execute("""
                CREATE TABLE topic (
                  id VARCHAR(64) PRIMARY KEY,
                  label VARCHAR(80) NOT NULL,
                  display_group VARCHAR(64) NOT NULL,
                  display_order INTEGER NOT NULL,
                  active BOOLEAN NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL
                )
                """);
        statement.execute("""
                CREATE TABLE post_topic (
                  post_id BIGINT NOT NULL,
                  topic_id VARCHAR(64) NOT NULL,
                  created_at TIMESTAMPTZ NOT NULL,
                  PRIMARY KEY (post_id, topic_id)
                )
                """);
        statement.execute("INSERT INTO post VALUES (42)");
        statement.execute("""
                INSERT INTO topic
                  (id, label, display_group, display_order, active, created_at)
                VALUES ('housing', 'Housing', 'Society', 1, true, now())
                """);
        statement.execute("INSERT INTO post_topic VALUES (42, 'housing', now())");
    }

    private static void assertValue(Statement statement, String sql, Object expected) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            assertEquals(expected, result.getObject(1));
        }
    }

    private static void resetSearchPath(Connection connection) throws Exception {
        try (Statement reset = connection.createStatement()) {
            reset.execute("RESET search_path");
        }
    }
}
