package com.yoursay;

import com.yoursay.posts.MediaType;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.posts.model.PostMediaUpload;
import com.yoursay.posts.model.PostMediaUploadRepository;
import io.agroal.api.AgroalDataSource;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.vertx.VertxContextSupport;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.net.URI;
import java.time.Instant;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests over the real reactive Postgres (Dev Services). The author rest-client and the
 * S3 presigner are mocked so the suite asserts wiring, persistence and the contract without AWS.
 */
@QuarkusTest
@TestSecurity(user = "author@yoursay.com", roles = "user")
public class PostControllerTest {

    private static final String AUTHOR_EMAIL = "author@yoursay.com";
    private static final long AUTHOR_ID = 42L;
    // A different author who never posts — used to prove getByUser actually filters by author.
    private static final long OTHER_AUTHOR_ID = 7L;

    @InjectMock
    @RestClient
    UserServiceClient userServiceClient;

    @InjectMock
    S3Presigner presigner;

    @Inject
    PostMediaUploadRepository uploadRepository;

    @Inject
    AgroalDataSource dataSource;

    @BeforeEach
    public void setup() throws Exception {
        Mockito.reset(userServiceClient, presigner);

        // The forwarded bearer resolves to an active official publisher and a known user id.
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "OFFICIAL", "ACTIVE", true)));

        PresignedPutObjectRequest put = Mockito.mock(PresignedPutObjectRequest.class);
        Mockito.when(put.url()).thenReturn(URI.create("https://s3.local/upload?sig=put").toURL());
        Mockito.when(presigner.presignPutObject(Mockito.any(
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.class)))
                .thenReturn(put);

        PresignedGetObjectRequest get = Mockito.mock(PresignedGetObjectRequest.class);
        Mockito.when(get.url()).thenReturn(URI.create("https://s3.local/download?sig=get").toURL());
        Mockito.when(presigner.presignGetObject(Mockito.any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(get);
    }

    private static String createBody(String summary, String question) {
        return """
                { "summary": "%s", "supportQuestion": "%s",
                  "media": [] }
                """.formatted(summary, question);
    }

    private static int createPost(String question) {
        return given().contentType("application/json")
                .body(createBody("Article context for the vote.", question))
                .when().post("/posts")
                .then().statusCode(201)
                .extract().path("id");
    }

    private static String presignKey(String mediaType, String contentType) {
        return given()
                .contentType("application/json")
                .body("""
                        { "mediaType": "%s", "contentType": "%s" }
                        """.formatted(mediaType, contentType))
                .when().post("/posts/media/presign")
                .then()
                .statusCode(200)
                .extract().path("s3Key");
    }

    private void insertUpload(Long userId, MediaType mediaType, String s3Key, String contentType,
                              Instant expiresAt) {
        // Panache reactive needs a Vert.x context + session; the JUnit thread has neither, so run
        // the setup write on a managed context rather than awaiting the Uni directly.
        try {
            VertxContextSupport.subscribeAndAwait(() -> Panache.withTransaction(
                    () -> uploadRepository.saveUpload(
                            new PostMediaUpload(userId, mediaType, s3Key, contentType, expiresAt))));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private long countPosts() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("select count(*) from post");
             ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getLong(1);
        }
    }

    @Test
    public void presignReturnsAKeyUploadUrlAndTtl() {
        ArgumentCaptor<PutObjectPresignRequest> request =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        ArgumentCaptor<String> auth = ArgumentCaptor.forClass(String.class);

        given()
                .header("Authorization", "Bearer presign-token-123")
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(200)
                .body("s3Key", startsWith("posts/"))
                .body("s3Key", endsWith(".jpg"))
                .body("uploadUrl", is("https://s3.local/upload?sig=put"))
                .body("expiresInSeconds", is(900));

        Mockito.verify(presigner).presignPutObject(request.capture());
        PutObjectPresignRequest actual = request.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(Duration.ofMinutes(15), actual.signatureDuration());
        org.junit.jupiter.api.Assertions.assertEquals("post-videos", actual.putObjectRequest().bucket());
        org.junit.jupiter.api.Assertions.assertEquals("image/jpeg", actual.putObjectRequest().contentType());
        org.junit.jupiter.api.Assertions.assertTrue(actual.putObjectRequest().key().startsWith("posts/"));
        Mockito.verify(userServiceClient).getCurrentUserAccess(auth.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer presign-token-123", auth.getValue());
    }

    @Test
    public void presignRejectsAnUnknownAuthor() {
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().nullItem());

        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(401);

        Mockito.verify(presigner, Mockito.never()).presignPutObject(Mockito.any(PutObjectPresignRequest.class));
    }

    @Test
    public void presignRejectsMismatchedMediaAndContentType() {
        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"video/mp4\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(400);

        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"VIDEO\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(400);
    }

    @Test
    public void createThenGetRoundTripsWithMediaAndPresignedUrls() {
        // Two media: an image with no poster, and a video WITH a poster — covers both branches of
        // presignDownload (null poster key -> null url; present poster key -> presigned url) and
        // proves media ordering is preserved.
        String imageKey = presignKey("IMAGE", "image/jpeg");
        String videoKey = presignKey("VIDEO", "video/mp4");
        String posterKey = presignKey("IMAGE", "image/jpeg");

        String body = """
                { "summary": "Body summary", "supportQuestion": "Should large platforms be accountable?",
                  "caseFor": "Reach means responsibility.", "caseAgainst": "Scale makes it unenforceable.",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s", "contentType": "image/jpeg",
                      "posterS3Key": null },
                    { "mediaType": "VIDEO", "orientation": "PORTRAIT", "s3Key": "%s", "contentType": "video/mp4",
                      "posterS3Key": "%s" }
                  ] }
                """.formatted(imageKey, videoKey, posterKey);

        int id = given()
                .contentType("application/json")
                .body(body)
                .when().post("/posts")
                .then()
                .statusCode(201)
                .body("userId", is((int) AUTHOR_ID))
                .body("$", not(hasKey("title")))
                .body("summary", is("Body summary"))
                .body("supportQuestion", is("Should large platforms be accountable?"))
                .body("caseFor", is("Reach means responsibility."))
                .body("caseAgainst", is("Scale makes it unenforceable."))
                .body("isUnbiased", is(false))
                .body("createdAt", notNullValue())
                .body("media.size()", is(2))
                .body("media[0].mediaType", is("IMAGE"))
                .body("media[0].orientation", is("LANDSCAPE"))    // omitted in the body -> defaults
                .body("media[0].s3Key", is(imageKey))
                .body("media[0].contentType", is("image/jpeg"))
                .body("media[0].url", is("https://s3.local/download?sig=get"))
                .body("media[0].posterS3Key", nullValue())
                .body("media[0].posterUrl", nullValue())          // null key -> null url
                .body("media[1].mediaType", is("VIDEO"))
                .body("media[1].orientation", is("PORTRAIT"))     // sent explicitly
                .body("media[1].s3Key", is(videoKey))
                .body("media[1].contentType", is("video/mp4"))
                .body("media[1].posterS3Key", is(posterKey))
                .body("media[1].posterUrl", is("https://s3.local/download?sig=get")) // present -> url
                .extract().path("id");

        given()
                .when().get("/posts/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("userId", is((int) AUTHOR_ID))
                .body("$", not(hasKey("title")))
                .body("summary", is("Body summary"))
                .body("supportQuestion", is("Should large platforms be accountable?"))
                .body("isUnbiased", is(false))
                .body("createdAt", notNullValue())
                // order survives the round trip: IMAGE before VIDEO
                .body("media.mediaType", contains("IMAGE", "VIDEO"))
                .body("media.orientation", contains("LANDSCAPE", "PORTRAIT"))
                .body("media[0].s3Key", is(imageKey))
                .body("media[0].contentType", is("image/jpeg"))
                .body("media[0].url", is("https://s3.local/download?sig=get"))
                .body("media[0].posterUrl", nullValue())
                .body("media[1].s3Key", is(videoKey))
                .body("media[1].contentType", is("video/mp4"))
                .body("media[1].posterS3Key", is(posterKey))
                .body("media[1].posterUrl", is("https://s3.local/download?sig=get"));
    }

    @Test
    public void createThenGetRoundTripsOrderedMultipleChoiceOptions() {
        String body = """
                { "summary": "Transport budget context.",
                  "supportQuestion": "Which transport change should happen first?",
                  "votingType": "MULTIPLE_CHOICE",
                  "voteOptions": [
                    {"label":"  More frequent buses  "},
                    {"label":"Protected cycle lanes"},
                    {"label":"Lower parking charges"}
                  ],
                  "media": [] }
                """;

        int postId = given().contentType("application/json").body(body)
                .when().post("/posts").then().statusCode(201)
                .body("votingType", is("MULTIPLE_CHOICE"))
                .body("voteOptions.label", contains(
                        "More frequent buses", "Protected cycle lanes", "Lower parking charges"))
                .body("voteOptions.ordinal", contains(0, 1, 2))
                .body("voteOptions.semanticKey", everyItem(nullValue()))
                .extract().path("id");

        given().when().get("/posts/" + postId).then().statusCode(200)
                .body("votingType", is("MULTIPLE_CHOICE"))
                .body("voteOptions.label", contains(
                        "More frequent buses", "Protected cycle lanes", "Lower parking charges"))
                .body("voteOptions.id", everyItem(greaterThan(0)));
    }

    @Test
    public void createDefaultsToServerOwnedBinaryOptionsAndRejectsAuthoredBinaryOptions() {
        given().contentType("application/json")
                .body(createBody("Binary context.", "Should the proposal proceed?"))
                .when().post("/posts").then().statusCode(201)
                .body("votingType", is("BINARY"))
                .body("voteOptions.label", contains("Agree", "Disagree"))
                .body("voteOptions.semanticKey", contains("AGREE", "DISAGREE"));

        given().contentType("application/json").body("""
                {"summary":"Invalid binary options.","supportQuestion":"Should this fail?",
                 "votingType":"BINARY","voteOptions":[{"label":"Yes"},{"label":"No"}],"media":[]}
                """).when().post("/posts").then().statusCode(400)
                .body("code", is("POST_VOTE_OPTIONS_INVALID"));
    }

    @Test
    public void createRejectsCaseInsensitiveDuplicateMultipleChoiceOptionsWithoutWritingAPost() throws Exception {
        long before = countPosts();
        given().contentType("application/json").body("""
                {"summary":"Duplicate choices.","supportQuestion":"Which route?",
                 "votingType":"MULTIPLE_CHOICE",
                 "voteOptions":[{"label":"Protected cycle lanes"},{"label":" protected CYCLE lanes "}],
                 "media":[]}
                """).when().post("/posts").then().statusCode(400)
                .body("code", is("POST_VOTE_OPTIONS_INVALID"));
        org.junit.jupiter.api.Assertions.assertEquals(before, countPosts());
    }

    @Test
    public void createIgnoresUserIdAndIsUnbiasedInBody() {
        // Body tries to spoof a different author and force the unbiased badge — both must be ignored.
        String json = """
                { "userId": 999, "isUnbiased": true,
                  "summary": "Trying to spoof", "supportQuestion": "Should this be trusted?", "media": [] }
                """;

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(201)
                .body("userId", is((int) AUTHOR_ID))   // from token, not the body's 999
                .body("isUnbiased", is(false));         // forced false, not the body's true
    }

    @Test
    public void getByIdReturns204WhenMissing() {
        given()
                .when().get("/posts/999999")
                .then()
                .statusCode(204);
    }

    @Test
    public void listByAuthorReturnsOnlyThatAuthorsPosts() {
        int earlier = createPost("Should the first proposal pass?");
        int later = createPost("Should the second proposal pass?");

        given()
                .when().get("/posts/user/" + AUTHOR_ID)
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("userId", everyItem(is((int) AUTHOR_ID)))
                // both this test's posts are present...
                .body("id", hasItems(earlier, later))
                // ...newest of the two ordered before the older (relative order, collision-safe)
                .body("findAll { it.supportQuestion == 'Should the first proposal pass?' || "
                                + "it.supportQuestion == 'Should the second proposal pass?' }.supportQuestion",
                        contains("Should the second proposal pass?", "Should the first proposal pass?"));
    }

    @Test
    public void listByAuthorWithNoPostsIsEmpty_provingTheFilterActuallyFilters() {
        // Posts exist for AUTHOR_ID; a query for a DIFFERENT author must come back empty. A query
        // that ignored the userId filter would wrongly return AUTHOR_ID's posts here.
        createPost("A post owned by AUTHOR_ID");

        given()
                .when().get("/posts/user/" + OTHER_AUTHOR_ID)
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    @Test
    public void createForwardsTheCallersBearerToUserService() {
        // Regression for the create-post "network error": user-service's author lookup is role-gated,
        // so post-service must forward the caller's bearer. Dropping it calls with a null Authorization
        // and user-service answers 401 (exactly the failure seen in the running app).
        org.mockito.ArgumentCaptor<String> auth = org.mockito.ArgumentCaptor.forClass(String.class);

        given()
                .header("Authorization", "Bearer test-jwt-123")
                .contentType("application/json")
                .body(createBody("Context for an authenticated post.", "Should this request be published?"))
                .when().post("/posts")
                .then()
                .statusCode(201);

        Mockito.verify(userServiceClient).getCurrentUserAccess(auth.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer test-jwt-123", auth.getValue());
    }

    @Test
    public void createRejectsAnUnknownAuthor() throws Exception {
        // The token email resolves to no user in user-service -> the author can't be established.
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().nullItem());
        long before = countPosts();

        given()
                .contentType("application/json")
                .body(createBody("Context from an unknown author.", "Should this orphan post exist?"))
                .when().post("/posts")
                .then()
                .statusCode(401)
                .body("code", is("POST_AUTHOR_NOT_FOUND"));

        org.junit.jupiter.api.Assertions.assertEquals(before, countPosts());
    }

    @Test
    public void createRejectsAStandardAccountEvenWhenAuthenticated() throws Exception {
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "STANDARD", "NONE", false)));
        long before = countPosts();

        given()
                .contentType("application/json")
                .body(createBody("Context from a reader.", "Should readers be able to publish?"))
                .when().post("/posts")
                .then()
                .statusCode(403)
                .body("code", is("POST_PUBLISHING_FORBIDDEN"));

        org.junit.jupiter.api.Assertions.assertEquals(before, countPosts());
    }

    @Test
    public void createRejectsContradictoryAccessDataRatherThanTrustingOneFlag() throws Exception {
        long before = countPosts();

        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "STANDARD", "NONE", true)));
        given()
                .contentType("application/json")
                .body(createBody("A standard account with a bad flag.", "Should this be rejected?"))
                .when().post("/posts")
                .then()
                .statusCode(403)
                .body("code", is("POST_PUBLISHING_FORBIDDEN"));

        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "OFFICIAL", "ACTIVE", false)));
        given()
                .contentType("application/json")
                .body(createBody("An official account with a denied capability.", "Should this be rejected too?"))
                .when().post("/posts")
                .then()
                .statusCode(403)
                .body("code", is("POST_PUBLISHING_FORBIDDEN"));

        org.junit.jupiter.api.Assertions.assertEquals(before, countPosts());
    }

    @Test
    public void presignRejectsASuspendedOfficialBeforeMintingAnUploadUrl() {
        Mockito.when(userServiceClient.getCurrentUserAccess(Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserAccess(
                        AUTHOR_ID, "OFFICIAL", "SUSPENDED", false)));

        given()
                .contentType("application/json")
                .body("{ \"mediaType\": \"IMAGE\", \"contentType\": \"image/jpeg\" }")
                .when().post("/posts/media/presign")
                .then()
                .statusCode(403)
                .body("code", is("POST_PUBLISHING_FORBIDDEN"));

        Mockito.verify(presigner, Mockito.never())
                .presignPutObject(Mockito.any(PutObjectPresignRequest.class));
    }

    @Test
    public void createRejectsBlankRequiredFields() {
        given()
                .contentType("application/json")
                .body("{ \"summary\": \"\", \"supportQuestion\": \"  \", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createDoesNotRequireALegacyTitle() {
        given()
                .contentType("application/json")
                .body("{ \"summary\": \"A complete summary.\", \"supportQuestion\": \"Should this be published?\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(201)
                .body("$", not(hasKey("title")))
                .body("supportQuestion", is("Should this be published?"));
    }

    @Test
    public void createRejectsBlankSummaryOnly() {
        given()
                .contentType("application/json")
                .body("{ \"summary\": \"  \", \"supportQuestion\": \"Should this be published?\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsBlankSupportQuestionOnly() {
        given()
                .contentType("application/json")
                .body("{ \"summary\": \"A summary\", \"supportQuestion\": \"  \", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsNullMediaItem() {
        given()
                .contentType("application/json")
                .body("""
                        { "summary": "A summary",
                          "supportQuestion": "Should this be published?", "media": [null] }
                        """)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsMediaItemMissingKey() {
        given()
                .contentType("application/json")
                .body("""
                        { "summary": "A summary",
                          "supportQuestion": "Should this be published?",
                          "media": [
                            { "mediaType": "IMAGE", "contentType": "image/jpeg", "posterS3Key": null }
                          ] }
                        """)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsMediaItemMissingContentType() {
        given()
                .contentType("application/json")
                .body("""
                        { "summary": "A summary",
                          "supportQuestion": "Should this be published?",
                          "media": [
                            { "mediaType": "IMAGE", "s3Key": "posts/missing-content-type.jpg",
                              "posterS3Key": null }
                          ] }
                        """)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsMediaKeyThatWasNotPresignedForTheAuthor() {
        String key = "posts/other-user-" + UUID.randomUUID() + ".jpg";
        String question = "Should rejected media be published " + UUID.randomUUID() + "?";
        insertUpload(OTHER_AUTHOR_ID, MediaType.IMAGE, key, "image/jpeg", Instant.now().plusSeconds(900));

        String json = """
                { "summary": "A summary", "supportQuestion": "%s",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(question, key);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);

        given()
                .when().get("/posts/user/" + AUTHOR_ID)
                .then()
                .statusCode(200)
                .body("supportQuestion", not(hasItem(question)));
    }

    @Test
    public void createRejectsMismatchedMediaContentType() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "summary": "A summary", "supportQuestion": "Should this image be published?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/png", "posterS3Key": null }
                  ] }
                """.formatted(key);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsMismatchedMediaReservationType() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "summary": "A summary", "supportQuestion": "Should this video be published?",
                  "media": [
                    { "mediaType": "VIDEO", "s3Key": "%s",
                      "contentType": "video/mp4", "posterS3Key": null }
                  ] }
                """.formatted(key);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsPosterKeyNotPresignedForTheAuthor() {
        String videoKey = presignKey("VIDEO", "video/mp4");
        String posterKey = "posts/other-user-poster-" + UUID.randomUUID() + ".jpg";
        insertUpload(OTHER_AUTHOR_ID, MediaType.IMAGE, posterKey, "image/jpeg", Instant.now().plusSeconds(900));

        String json = """
                { "summary": "A summary", "supportQuestion": "Should this video be published?",
                  "media": [
                    { "mediaType": "VIDEO", "s3Key": "%s",
                      "contentType": "video/mp4", "posterS3Key": "%s" }
                  ] }
                """.formatted(videoKey, posterKey);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsDuplicateMediaKeysInOneRequest() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "summary": "A summary", "supportQuestion": "Should these images be published?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null },
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(key, key);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsExpiredMediaUpload() {
        String key = "posts/expired-" + UUID.randomUUID() + ".jpg";
        insertUpload(AUTHOR_ID, MediaType.IMAGE, key, "image/jpeg", Instant.now().minusSeconds(1));
        String json = """
                { "summary": "A summary", "supportQuestion": "Should this expired image be published?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(key);

        given()
                .contentType("application/json")
                .body(json)
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsMoreThanEightMediaItems() {
        StringBuilder media = new StringBuilder("[");
        for (int i = 0; i < 9; i++) {
            if (i > 0) {
                media.append(',');
            }
            String key = presignKey("IMAGE", "image/jpeg");
            media.append("""
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                    """.formatted(key));
        }
        media.append(']');

        given()
                .contentType("application/json")
                .body("""
                        { "summary": "A summary",
                          "supportQuestion": "Should all these images be published?", "media": %s }
                        """.formatted(media))
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsReusedMediaKey() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "summary": "A summary", "supportQuestion": "Should this first use be published?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(key);

        given().contentType("application/json").body(json)
                .when().post("/posts")
                .then().statusCode(201);

        given().contentType("application/json").body(json.replace("first use", "second use"))
                .when().post("/posts")
                .then().statusCode(400);
    }

    @Test
    public void recentFeedContainsCreatedPostsNewestFirst() {
        // Two posts created in order; in the feed the later one must come before the earlier one.
        // Asserting their RELATIVE order (not an absolute index) is robust to other tests' posts.
        int earlier = createPost("Feed earlier");
        int later = createPost("Feed later");

        java.util.List<Integer> ids = given()
                .queryParam("size", 50)
                .when().get("/posts")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .extract().jsonPath().getList("id", Integer.class);

        org.junit.jupiter.api.Assertions.assertTrue(ids.contains(earlier) && ids.contains(later),
                "feed should contain both created posts");
        org.junit.jupiter.api.Assertions.assertTrue(ids.indexOf(later) < ids.indexOf(earlier),
                "newest-first: the later post must appear before the earlier one");
    }

    @Test
    public void recentFeedDefaultsToFivePostsPerPage() {
        for (int i = 0; i < 6; i++) {
            createPost("Default page post " + i);
        }

        given()
                .when().get("/posts")
                .then()
                .statusCode(200)
                .body("size()", is(5));
    }

    @Test
    public void recentFeedPagesReconstructTheNewestFirstSequenceWithoutOverlap() {
        // Ensure at least two pages' worth of posts exist, then prove offset paging matches the
        // single newest-first sequence: page(0,5) is its first five and page(1,5) the next five.
        // Comparing both pages against ONE reference query makes this robust to whatever other
        // tests inserted — we assert the paging CONTRACT, not an absolute position in the table.
        for (int i = 0; i < 10; i++) {
            createPost("Paged post " + i);
        }

        java.util.List<Integer> all = given()
                .queryParam("page", 0).queryParam("size", 50)
                .when().get("/posts")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(all.size() >= 10, "need at least two pages of posts");

        java.util.List<Integer> pageZero = given()
                .queryParam("page", 0).queryParam("size", 5)
                .when().get("/posts")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", Integer.class);

        java.util.List<Integer> pageOne = given()
                .queryParam("page", 1).queryParam("size", 5)
                .when().get("/posts")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", Integer.class);

        org.junit.jupiter.api.Assertions.assertEquals(all.subList(0, 5), pageZero,
                "page 0 must be the five newest posts, in order");
        org.junit.jupiter.api.Assertions.assertEquals(all.subList(5, 10), pageOne,
                "page 1 must continue with the next five, in order");
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Collections.disjoint(pageZero, pageOne),
                "consecutive pages must not overlap");
    }

    @Test
    public void recentFeedCapsPageSizeAtFifty() {
        // Asking for more than the cap yields at most 50 — a client can't pull the whole table.
        for (int i = 0; i < 55; i++) {
            createPost("Cap post " + i);
        }

        given()
                .queryParam("size", 1000)
                .when().get("/posts")
                .then()
                .statusCode(200)
                .body("size()", is(50));
    }
}
