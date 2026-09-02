package com.yoursay.unwrapped.model;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class UnwrappedStoryRepositoryTest {
    @Inject
    UnwrappedStoryRepository stories;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void newestApprovedSelectsTheLatestEligibleMilestoneInTheDatabase() throws Exception {
        long postId = createPost("Repository selection");
        long otherPostId = createPost("Other post");
        try {
            UUID firstMilestone = insertStory(postId, 100, "APPROVED",
                    Instant.parse("2026-07-28T09:00:00Z"));
            insertStory(postId, 250, "APPROVED", null);
            UUID expected = insertStory(postId, 250, "APPROVED",
                    Instant.parse("2026-07-28T10:00:00Z"));
            insertStory(postId, 300, "DRAFT", null);
            insertStory(postId, 500, "APPROVED",
                    Instant.parse("2026-07-28T11:00:00Z"));
            insertStory(otherPostId, 250, "APPROVED",
                    Instant.parse("2026-07-28T12:00:00Z"));

            assertEquals(expected, stories.newestApproved(postId, 400).orElseThrow().getId());
            assertEquals(expected, stories.newestApproved(postId, 250).orElseThrow().getId());
            assertEquals(firstMilestone,
                    stories.newestApproved(postId, 249).orElseThrow().getId());
            assertTrue(stories.newestApproved(postId, 99).isEmpty());
        } finally {
            deletePost(postId);
            deletePost(otherPostId);
        }
    }

    private long createPost(String summary) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into post(
                         user_id, summary, support_question, is_ai_generated,
                         created_at, updated_at, voting_type, jurisdiction
                     ) values (1, ?, 'Should this story be selected?',
                         false, now(), now(), 'BINARY', 'GLOBAL')
                     returning id
                     """)) {
            statement.setString(1, summary);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private UUID insertStory(long postId, int milestone, String reviewStatus,
                             Instant reviewedAt) throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        String analysisVersion = "repository-test-" + jobId;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement job = connection.prepareStatement("""
                    insert into unwrapped_analysis_job(
                        id, post_id, milestone, analysis_version, status, attempt_count, created_at
                    ) values (?, ?, ?, ?, 'DRAFT_READY', 0, now())
                    """)) {
                job.setObject(1, jobId);
                job.setLong(2, postId);
                job.setInt(3, milestone);
                job.setString(4, analysisVersion);
                job.executeUpdate();
            }
            try (PreparedStatement story = connection.prepareStatement("""
                    insert into unwrapped_story(
                        id, job_id, post_id, milestone, canonical_vote_count, aggregate_version,
                        story_schema_version, analysis_version, prompt_version, rule_set_version,
                        model, story_json, review_status, generated_at, reviewed_at
                    ) values (?, ?, ?, ?, ?, ?, 'unwrapped-story-v1', ?,
                        'unwrapped-case-v1', 'cohort-rules-v1', 'repository-test-model',
                        '{"pages":[],"sources":[]}'::jsonb, ?, now(), ?)
                    """)) {
                story.setObject(1, storyId);
                story.setObject(2, jobId);
                story.setLong(3, postId);
                story.setInt(4, milestone);
                story.setLong(5, milestone);
                story.setString(6, "aggregate-" + jobId);
                story.setString(7, analysisVersion);
                story.setString(8, reviewStatus);
                if (reviewedAt == null) {
                    story.setTimestamp(9, null);
                } else {
                    story.setTimestamp(9, Timestamp.from(reviewedAt));
                }
                story.executeUpdate();
            }
        }
        return storyId;
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
