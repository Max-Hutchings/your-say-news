package com.yoursay.user.user.model;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Small JDBC read model used by the request-wide account activation guard.
 *
 * Keeping this lookup independent of Panache avoids crossing the blocking and reactive Panache
 * enhancement paths in requests served by this combined application.
 */
@ApplicationScoped
public class AccountActivationRepository {

    @Inject
    AgroalDataSource dataSource;

    public Boolean findActiveByEmail(String email) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select active from your_say_user where email = ?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getBoolean("active") : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to check account activation", exception);
        }
    }
}
