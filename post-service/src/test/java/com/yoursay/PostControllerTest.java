package com.yoursay;

import com.yoursay.posts.MediaType;
import com.yoursay.posts.client.UserServiceClient;
import com.yoursay.posts.model.PostMediaUpload;
import com.yoursay.posts.model.PostMediaUploadRepository;
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

    @BeforeEach
    public void setup() throws Exception {
        Mockito.reset(userServiceClient, presigner);

        // The authenticated email resolves to a known user id (any forwarded bearer is accepted here).
        Mockito.when(userServiceClient.getUserByEmail(Mockito.eq(AUTHOR_EMAIL), Mockito.any()))
                .thenReturn(Uni.createFrom().item(new UserServiceClient.UserRef(AUTHOR_ID)));

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

    private static String createBody(String title, String summary, String question) {
        return """
                { "title": "%s", "summary": "%s", "supportQuestion": "%s",
                  "media": [] }
                """.formatted(title, summary, question);
    }

    private static int createPost(String title) {
        return given().contentType("application/json")
                .body(createBody(title, "s", "q"))
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
        Mockito.verify(userServiceClient).getUserByEmail(Mockito.eq(AUTHOR_EMAIL), auth.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer presign-token-123", auth.getValue());
    }

    @Test
    public void presignRejectsAnUnknownAuthor() {
        Mockito.when(userServiceClient.getUserByEmail(Mockito.eq(AUTHOR_EMAIL), Mockito.any()))
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
                { "title": "Headline", "summary": "Body summary", "supportQuestion": "Do you agree?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s", "contentType": "image/jpeg",
                      "posterS3Key": null },
                    { "mediaType": "VIDEO", "s3Key": "%s", "contentType": "video/mp4",
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
                .body("title", is("Headline"))
                .body("summary", is("Body summary"))
                .body("supportQuestion", is("Do you agree?"))
                .body("isUnbiased", is(false))
                .body("createdAt", notNullValue())
                .body("media.size()", is(2))
                .body("media[0].mediaType", is("IMAGE"))
                .body("media[0].s3Key", is(imageKey))
                .body("media[0].contentType", is("image/jpeg"))
                .body("media[0].url", is("https://s3.local/download?sig=get"))
                .body("media[0].posterS3Key", nullValue())
                .body("media[0].posterUrl", nullValue())          // null key -> null url
                .body("media[1].mediaType", is("VIDEO"))
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
                .body("title", is("Headline"))
                .body("summary", is("Body summary"))
                .body("supportQuestion", is("Do you agree?"))
                .body("isUnbiased", is(false))
                .body("createdAt", notNullValue())
                // order survives the round trip: IMAGE before VIDEO
                .body("media.mediaType", contains("IMAGE", "VIDEO"))
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
    public void createIgnoresUserIdAndIsUnbiasedInBody() {
        // Body tries to spoof a different author and force the unbiased badge — both must be ignored.
        String json = """
                { "userId": 999, "isUnbiased": true, "title": "Spoof",
                  "summary": "Trying to spoof", "supportQuestion": "Really?", "media": [] }
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
        int earlier = createPost("First by author");
        int later = createPost("Second by author");

        given()
                .when().get("/posts/user/" + AUTHOR_ID)
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(2))
                .body("userId", everyItem(is((int) AUTHOR_ID)))
                // both this test's posts are present...
                .body("id", hasItems(earlier, later))
                // ...newest of the two ordered before the older (relative order, collision-safe)
                .body("findAll { it.title == 'First by author' || it.title == 'Second by author' }.title",
                        contains("Second by author", "First by author"));
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
                .body(createBody("Auth header forwarded", "s", "q"))
                .when().post("/posts")
                .then()
                .statusCode(201);

        Mockito.verify(userServiceClient).getUserByEmail(Mockito.eq(AUTHOR_EMAIL), auth.capture());
        org.junit.jupiter.api.Assertions.assertEquals("Bearer test-jwt-123", auth.getValue());
    }

    @Test
    public void createRejectsAnUnknownAuthor() {
        // The token email resolves to no user in user-service -> the author can't be established.
        Mockito.when(userServiceClient.getUserByEmail(Mockito.eq(AUTHOR_EMAIL), Mockito.any()))
                .thenReturn(Uni.createFrom().nullItem());

        given()
                .contentType("application/json")
                .body(createBody("Orphan", "s", "q"))
                .when().post("/posts")
                .then()
                .statusCode(401);
    }

    @Test
    public void createRejectsBlankRequiredFields() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"  \", \"summary\": \"\", \"supportQuestion\": \"  \", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsBlankTitleOnly() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"  \", \"summary\": \"A summary\", \"supportQuestion\": \"Do you agree?\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsBlankSummaryOnly() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"A headline\", \"summary\": \"  \", \"supportQuestion\": \"Do you agree?\", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsBlankSupportQuestionOnly() {
        given()
                .contentType("application/json")
                .body("{ \"title\": \"A headline\", \"summary\": \"A summary\", \"supportQuestion\": \"  \", \"media\": [] }")
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsNullMediaItem() {
        given()
                .contentType("application/json")
                .body("""
                        { "title": "A headline", "summary": "A summary",
                          "supportQuestion": "Do you agree?", "media": [null] }
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
                        { "title": "A headline", "summary": "A summary",
                          "supportQuestion": "Do you agree?",
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
                        { "title": "A headline", "summary": "A summary",
                          "supportQuestion": "Do you agree?",
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
        String title = "Rejected other user media " + UUID.randomUUID();
        insertUpload(OTHER_AUTHOR_ID, MediaType.IMAGE, key, "image/jpeg", Instant.now().plusSeconds(900));

        String json = """
                { "title": "%s", "summary": "A summary", "supportQuestion": "Do you agree?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(title, key);

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
                .body("title", not(hasItem(title)));
    }

    @Test
    public void createRejectsMismatchedMediaContentType() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "title": "Wrong content type", "summary": "A summary", "supportQuestion": "Do you agree?",
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
                { "title": "Wrong media type", "summary": "A summary", "supportQuestion": "Do you agree?",
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
                { "title": "Bad poster", "summary": "A summary", "supportQuestion": "Do you agree?",
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
                { "title": "Duplicate keys", "summary": "A summary", "supportQuestion": "Do you agree?",
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
                { "title": "Expired media", "summary": "A summary", "supportQuestion": "Do you agree?",
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
                        { "title": "Too much media", "summary": "A summary",
                          "supportQuestion": "Do you agree?", "media": %s }
                        """.formatted(media))
                .when().post("/posts")
                .then()
                .statusCode(400);
    }

    @Test
    public void createRejectsReusedMediaKey() {
        String key = presignKey("IMAGE", "image/jpeg");
        String json = """
                { "title": "First use", "summary": "A summary", "supportQuestion": "Do you agree?",
                  "media": [
                    { "mediaType": "IMAGE", "s3Key": "%s",
                      "contentType": "image/jpeg", "posterS3Key": null }
                  ] }
                """.formatted(key);

        given().contentType("application/json").body(json)
                .when().post("/posts")
                .then().statusCode(201);

        given().contentType("application/json").body(json.replace("First use", "Second use"))
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
    public void recentFeedReturnsAtMostFiftyPosts() {
        for (int i = 0; i < 51; i++) {
            createPost("Limit post " + i);
        }

        given()
                .when().get("/posts")
                .then()
                .statusCode(200)
                .body("size()", is(50))
                .body("title", not(hasItem("Limit post 0")))
                .body("title", hasItem("Limit post 50"));
    }
}
