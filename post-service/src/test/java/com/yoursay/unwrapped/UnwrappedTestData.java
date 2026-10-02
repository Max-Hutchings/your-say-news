package com.yoursay.unwrapped;

import io.agroal.api.AgroalDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** JDBC fixtures for Unwrapped integration tests: posts, bulk votes and lifecycle row counts. */
final class UnwrappedTestData {
    private final AgroalDataSource dataSource;

    UnwrappedTestData(AgroalDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** A binary post; returns its id and its Agree option id. */
    TestPost createPost(long authorId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(user_id, summary, support_question)
                     values (?, 'A council is reviewing evening busking permits.',
                             'Should street performers need a licence?')
                     returning id
                     """)) {
            post.setLong(1, authorId);
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    returning id
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                try (ResultSet result = options.executeQuery()) {
                    result.next();
                    return new TestPost(postId, result.getLong(1));
                }
            }
        }
    }

    /** The caller's own vote plus {@code others} votes from distinct synthetic users. */
    void insertVotes(TestPost post, long callerId, int others) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into votes(post_id, user_id, option_id, characteristic_snapshot)
                     select ?, ?, ?, '{}'::jsonb
                     union all
                     select ?, 980000 + generated.user_number, ?, '{}'::jsonb
                     from generate_series(1, ?) as generated(user_number)
                     """)) {
            statement.setLong(1, post.id());
            statement.setLong(2, callerId);
            statement.setLong(3, post.agreeOptionId());
            statement.setLong(4, post.id());
            statement.setLong(5, post.agreeOptionId());
            statement.setInt(6, others);
            assertEquals(others + 1, statement.executeUpdate());
        }
    }

    void markDirty(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into unwrapped_reconciliation(post_id, dirty_at) values (?, now())")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }

    int count(String table, long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from " + table + " where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    Integer jobMilestone(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select milestone from unwrapped_analysis_job where post_id = ?")) {
            statement.setLong(1, postId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : null;
            }
        }
    }

    void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from post where id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }

    record TestPost(long id, long agreeOptionId) {
    }
}
