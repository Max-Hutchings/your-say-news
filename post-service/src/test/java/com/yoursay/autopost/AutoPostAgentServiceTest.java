package com.yoursay.autopost;

import com.yoursay.posts.postagent.AutoPostAgentService;
import com.yoursay.posts.postagent.error.AgentApiException;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AutoPostAgentServiceTest {

    @Inject
    AutoPostAgentService service;

    @Inject
    AgroalDataSource dataSource;

    @Test
    void startsARecoverablePepperDraftForTheOfficialPublisher() throws Exception {
        long officialId = userId("yoursay");

        UUID jobId = service.startForPublisher(officialId, "  A bounded current-story brief.  ");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select user_id, prompt, status
                     from pepper_ai_draft_post where id = ?
                     """)) {
            statement.setObject(1, jobId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(officialId, result.getLong("user_id"));
                assertEquals("A bounded current-story brief.", result.getString("prompt"));
                assertTrue(List.of("RECEIVED", "GENERATING", "FINISHED", "FAILED")
                        .contains(result.getString("status")));
            }
        }
        assertTrue(service.getForPublisher(jobId, officialId).isPresent());
    }

    @Test
    void refusesTrustedHandoffForAnAdminEvenWithPublishingEnabled() throws Exception {
        long adminId = userId("yoursay.admin");
        setPublishingEnabled(adminId, true);
        try {
            AgentApiException error = assertThrows(AgentApiException.class,
                    () -> service.startForPublisher(adminId, "Story brief"));

            assertEquals("AGENT_PUBLISHING_FORBIDDEN", error.errorCode());
            assertEquals(403, error.statusCode());
        } finally {
            setPublishingEnabled(adminId, false);
        }
    }

    private void setPublishingEnabled(long userId, boolean enabled) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update your_say_user set publishing_enabled = ? where id = ?")) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, userId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test
    void retriesAFailedDraftWithItsExactPersistedPrompt() throws Exception {
        long officialId = userId("yoursay");
        UUID failedDraftId = UUID.randomUUID();
        String exactPrompt = "Original story brief with exact spacing\nAnd source order.";
        insertFailedDraft(failedDraftId, officialId, exactPrompt);

        UUID retriedDraftId = service.retryForPublisher(failedDraftId, officialId);

        assertFalse(failedDraftId.equals(retriedDraftId));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select prompt from pepper_ai_draft_post where id = ?")) {
            statement.setObject(1, retriedDraftId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(exactPrompt, result.getString("prompt"));
            }
        }
    }

    private void insertFailedDraft(UUID id, long userId, String prompt) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     insert into pepper_ai_draft_post
                         (id, user_id, prompt, replica_id, status, success, error_code,
                          error_message, version, created_at, updated_at, completed_at)
                     values (?, ?, ?, 'local', 'FAILED', false, 'AGENT_GENERATION_FAULT',
                             'Safe failure', 0, now(), now(), now())
                     """)) {
            statement.setObject(1, id);
            statement.setLong(2, userId);
            statement.setString(3, prompt);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private long userId(String handle) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select id from your_say_user where handle = ?")) {
            statement.setString(1, handle);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }
}
