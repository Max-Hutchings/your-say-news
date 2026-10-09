package com.yoursay.user.auth;

import com.yoursay.user.user.YourSayUserService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The resolver is excluded from the test profile (tests authenticate with @TestSecurity), so it is
 * built by hand over the real user service and database.
 */
@QuarkusTest
class DatabaseFirebaseRoleResolverTest {

    private static final String UNKNOWN_EMAIL = "never.signed.in@example.com";

    @Inject
    YourSayUserService userService;

    @Inject
    AgroalDataSource dataSource;

    private DatabaseFirebaseRoleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new DatabaseFirebaseRoleResolver(userService);
    }

    @Test
    void accountWithNoRowYetIsAUserSoItsFirstSignInCanProvisionIt() throws Exception {
        assertEquals(0, countUsersWithEmail(UNKNOWN_EMAIL));

        assertTrue(resolver.hasActiveUserAccess(UNKNOWN_EMAIL));
        assertFalse(resolver.hasActiveAdminAccess(UNKNOWN_EMAIL));
    }

    @Test
    void existingActiveAccountIsAUserButNotAnAdmin() {
        assertTrue(resolver.hasActiveUserAccess("jane.smith@example.com"));
        assertFalse(resolver.hasActiveAdminAccess("jane.smith@example.com"));
    }

    @Test
    void accountDeactivatedByAnAdministratorIsRefused() throws Exception {
        setUserActive("jane.smith@example.com", false);
        try {
            assertFalse(resolver.hasActiveUserAccess("jane.smith@example.com"));
        } finally {
            setUserActive("jane.smith@example.com", true);
        }
    }

    @Test
    void activeAdminIsAnAdminUntilDeactivated() throws Exception {
        assertTrue(resolver.hasActiveAdminAccess("admin@yoursay.com"));

        setUserActive("admin@yoursay.com", false);
        try {
            assertFalse(resolver.hasActiveAdminAccess("admin@yoursay.com"));
            assertFalse(resolver.hasActiveUserAccess("admin@yoursay.com"));
        } finally {
            setUserActive("admin@yoursay.com", true);
        }
    }

    private int countUsersWithEmail(String email) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from your_say_user where email = ?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void setUserActive(String email, boolean active) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update your_say_user set active = ? where email = ?")) {
            statement.setBoolean(1, active);
            statement.setString(2, email);
            statement.executeUpdate();
        }
    }
}
