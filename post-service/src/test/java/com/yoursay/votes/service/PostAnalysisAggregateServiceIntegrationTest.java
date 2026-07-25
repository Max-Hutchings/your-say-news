package com.yoursay.votes.service;

import com.yoursay.votes.PostAnalysisAggregateService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class PostAnalysisAggregateServiceIntegrationTest {
    @Inject
    PostAnalysisAggregateService aggregates;
    @Inject
    AgroalDataSource dataSource;

    @Test
    void repeatedCapturesOfUnchangedDataKeepOneVersion() throws Exception {
        long postId = createPost();
        try {
            var first = aggregates.capture(postId);
            var second = aggregates.capture(postId);

            assertNotNull(first.capturedAt());
            assertNotNull(second.capturedAt());
            assertEquals(first.aggregateVersion(), second.aggregateVersion());
        } finally {
            deletePost(postId);
        }
    }

    private long createPost() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement post = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_unbiased,
                         created_at, updated_at, voting_type, jurisdiction
                     ) values (1, 'Stable summary', 'Stable question?',
                         false, now(), now(), 'BINARY', 'GLOBAL')
                     returning id
                     """)) {
            long postId;
            try (ResultSet result = post.executeQuery()) {
                result.next();
                postId = result.getLong(1);
            }
            try (PreparedStatement options = connection.prepareStatement("""
                    insert into post_vote_option(post_id, label, ordinal, semantic_key)
                    values (?, 'Agree', 0, 'AGREE'), (?, 'Disagree', 1, 'DISAGREE')
                    """)) {
                options.setLong(1, postId);
                options.setLong(2, postId);
                options.executeUpdate();
            }
            return postId;
        }
    }

    private void deletePost(long postId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "delete from post where id = ?")) {
            statement.setLong(1, postId);
            statement.executeUpdate();
        }
    }
}
