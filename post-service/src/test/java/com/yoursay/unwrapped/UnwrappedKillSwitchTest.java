package com.yoursay.unwrapped;

import com.yoursay.unwrapped.UnwrappedTestData.TestPost;
import com.yoursay.unwrapped.service.UnwrappedReconciliationWorker;
import com.yoursay.votes.client.UserCharacteristicClient;
import io.agroal.api.AgroalDataSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** With {@code unwrapped.features.enabled=false} nothing can start or show Unwrapped content. */
@QuarkusTest
@TestProfile(UnwrappedKillSwitchTest.DisabledProfile.class)
class UnwrappedKillSwitchTest {
    private static final String READER_EMAIL = "reader@yoursay.com";
    private static final long READER_ID = 4501L;

    @InjectMock
    UserCharacteristicClient userClient;
    @Inject
    AgroalDataSource dataSource;
    @Inject
    UnwrappedReconciliationWorker reconciliationWorker;

    private UnwrappedTestData data;

    @BeforeEach
    void setUp() {
        data = new UnwrappedTestData(dataSource);
        Mockito.when(userClient.getUserByEmail(Mockito.eq(READER_EMAIL), Mockito.nullable(String.class)))
                .thenReturn(Response.ok(new UserCharacteristicClient.UserRef(READER_ID)).build());
    }

    @Test
    @TestSecurity(user = READER_EMAIL, roles = "user")
    void featuresReportUnwrappedDisabled() {
        given().when().get("/unwrapped/features")
                .then()
                .statusCode(200)
                .body("enabled", equalTo(false))
                .body("unwrapButton", equalTo(true));
    }

    @Test
    @TestSecurity(user = READER_EMAIL, roles = "user")
    void readerCanNeitherQueueNorReadNorAnswerUnwrapped() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, READER_ID, 600);

            given().when().post("/posts/" + post.id() + "/unwrapped/generate")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("UNWRAPPED_DISABLED"));
            given().when().get("/posts/" + post.id() + "/unwrapped")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("UNWRAPPED_DISABLED"));
            given().contentType("application/json")
                    .body(Map.of("optionId", post.agreeOptionId()))
                    .when().post("/posts/" + post.id() + "/unwrapped/"
                            + UUID.randomUUID() + "/follow-up")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("UNWRAPPED_DISABLED"));

            assertEquals(0, data.count("unwrapped_reconciliation", post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }

    @Test
    @TestSecurity(user = "admin@yoursay.com", roles = "admin")
    void adminCanNeitherQueueGenerationNorRunABenchmark() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, READER_ID, 600);

            given().when().post("/api/admin/unwrapped/posts/" + post.id() + "/generate")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("UNWRAPPED_DISABLED"));
            given().contentType("application/json")
                    .body(Map.of("systemPrompts", List.of("Write concise analysis.")))
                    .when().post("/api/admin/unwrapped/posts/" + post.id() + "/benchmark")
                    .then()
                    .statusCode(409)
                    .body("code", equalTo("UNWRAPPED_DISABLED"));

            assertEquals(0, data.count("unwrapped_reconciliation", post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }

    @Test
    void reconciliationLeavesQueuedWorkUntouchedWhileDisabled() throws Exception {
        TestPost post = data.createPost(READER_ID);
        try {
            data.insertVotes(post, READER_ID, 600);
            data.markDirty(post.id());

            reconciliationWorker.reconcileOne();

            assertEquals(0, data.count("unwrapped_analysis_job", post.id()));
            assertEquals(1, data.count("unwrapped_reconciliation", post.id()));
        } finally {
            data.deletePost(post.id());
        }
    }

    public static final class DisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("unwrapped.features.enabled", "false");
        }
    }
}
