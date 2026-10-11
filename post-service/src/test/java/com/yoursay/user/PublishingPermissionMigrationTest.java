package com.yoursay.user;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test database is migrated while empty, so migration 0019's carry-over of existing accounts
 * never runs there. This replays the real changelog up to 0018 in a scratch schema, inserts
 * accounts in the old publisher_status shape, then applies 0019 (ADR-066).
 */
@QuarkusTest
class PublishingPermissionMigrationTest {

    private static final String SCHEMA = "publishing_backfill_check";
    private static final String MIGRATION_0019 = "20261011-0019-1";

    @Inject
    AgroalDataSource dataSource;

    @Test
    void onlyActiveOfficialAccountsKeepPublishingWhenPublisherStatusIsReplaced() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            execute(connection, "drop schema if exists " + SCHEMA + " cascade");
            execute(connection, "create schema " + SCHEMA);
            try {
                execute(connection, "set search_path to " + SCHEMA);
                Liquibase liquibase = liquibaseIn(connection);
                liquibase.update(changeSetsBefore0019(liquibase), new Contexts(), new LabelExpression());
                insertLegacyAccounts(connection);

                liquibase.update(1, new Contexts(), new LabelExpression());

                assertEquals(Map.of(
                        "admin.none@example.com", false,
                        "official.active@example.com", true,
                        "official.suspended@example.com", false,
                        "reader.none@example.com", false
                ), publishingByEmail(connection));
            } finally {
                connection.setAutoCommit(true);
                execute(connection, "reset search_path");
                execute(connection, "drop schema if exists " + SCHEMA + " cascade");
            }
        }
    }

    private static Liquibase liquibaseIn(Connection connection) throws Exception {
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName(SCHEMA);
        database.setLiquibaseSchemaName(SCHEMA);
        return new Liquibase("db/db.changelog-master.xml", new ClassLoaderResourceAccessor(), database);
    }

    private static int changeSetsBefore0019(Liquibase liquibase) throws Exception {
        List<ChangeSet> changeSets = liquibase.getDatabaseChangeLog().getChangeSets();
        int index = 0;
        while (index < changeSets.size() && !MIGRATION_0019.equals(changeSets.get(index).getId())) {
            index++;
        }
        assertTrue(index < changeSets.size(), "migration 0019 is not in the master changelog");
        return index;
    }

    private static void insertLegacyAccounts(Connection connection) throws Exception {
        List<String[]> accounts = List.of(
                new String[]{"official.active@example.com", "OFFICIAL", "ACTIVE"},
                new String[]{"official.suspended@example.com", "OFFICIAL", "SUSPENDED"},
                new String[]{"admin.none@example.com", "ADMIN", "NONE"},
                new String[]{"reader.none@example.com", "USER", "NONE"});
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into your_say_user
                    (email, first_name, last_name, display_name, handle, created_date, active,
                     account_type, publisher_status)
                values (?, 'Legacy', 'Account', ?, ?, date '2026-01-15', true, ?, ?)
                """)) {
            for (String[] account : accounts) {
                String handle = account[0].substring(0, account[0].indexOf('@')).replace('.', '_');
                statement.setString(1, account[0]);
                statement.setString(2, "Legacy " + handle);
                statement.setString(3, handle);
                statement.setString(4, account[1]);
                statement.setString(5, account[2]);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private static Map<String, Boolean> publishingByEmail(Connection connection) throws Exception {
        Map<String, Boolean> publishing = new TreeMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select email, publishing_enabled from your_say_user");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                publishing.put(result.getString("email"), result.getBoolean("publishing_enabled"));
            }
        }
        return publishing;
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
