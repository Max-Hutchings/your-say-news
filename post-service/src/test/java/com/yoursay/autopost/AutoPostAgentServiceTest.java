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
    void refusesTrustedHandoffForAnAdminThatIsNotAnOfficialPublisher() throws Exception {
        AgentApiException error = assertThrows(AgentApiException.class,
                () -> service.startForPublisher(userId("yoursay.admin"), "Story brief"));

        assertEquals("AGENT_PUBLISHING_FORBIDDEN", error.errorCode());
        assertEquals(403, error.statusCode());
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
