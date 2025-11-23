package com.yoursay;

import com.yoursay.posts.UserServiceClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

// Annotation to tell Quarkus to start the application for testing
@QuarkusTest
public class PostControllerTest {

    // Test for saving a post using the POST /posts endpoint
    @InjectMock
    @RestClient
    UserServiceClient userServiceClient;

    @BeforeEach
    public void setup() {
        Mockito.when(userServiceClient.getUser(Mockito.anyLong())).thenReturn(Uni.createFrom().item(Response.ok().build()));
    }


    @Test
    public void testSavePost() {


        // JSON payload for the new post
        String json = "{\"userId\": 1, \"title\": \"Test Post\", \"description\": \"This is a test post.\", \"imageUrl\": \"http://example.com/image.jpg\"}";

        // Send POST request and verify the response
        given()
                .contentType("application/json") // Set the request content type
                .body(json) // Provide the JSON payload
                .when().post("/posts") // Call the POST endpoint
                .then()
                .log().all()
                .statusCode(200) // Expect HTTP 200 OK
                .body("userId", is(1)) // Verify the userId field
                .body("title", is("Test Post")) // Verify the title field
                .body("description", is("This is a test post.")) // Verify the description field
                .body("imageUrl", is("http://example.com/image.jpg")) // Verify the imageUrl field
                .body("id", notNullValue()); // Verify that an id has been generated
    }

    // Test for retrieving a post by id using the GET /posts/{id} endpoint
    @Test
    public void testGetPost() {
        // JSON payload for creating a new post
        String json = "{\"userId\": 2, \"title\": \"Another Post\", \"description\": \"Another test post.\", \"imageUrl\": \"http://example.com/image2.jpg\"}";

        // First, create the post and extract the generated id from the response
        int id = given()
                .contentType("application/json") // Set the request content type
                .body(json) // Provide the JSON payload
                .when().post("/posts") // Call the POST endpoint to save the post
                .then()
                .statusCode(200) // Expect HTTP 200 OK
                .extract().path("id"); // Extract the id from the response

        // Now, retrieve the created post using the GET endpoint with the extracted id
        given()
                .when().get("/posts/" + id) // Call the GET endpoint with the post id
                .then()
                .statusCode(200) // Expect HTTP 200 OK
                .body("id", is(id)) // Verify the id matches
                .body("userId", is(2)) // Verify the userId field
                .body("title", is("Another Post")) // Verify the title field
                .body("description", is("Another test post.")) // Verify the description field
                .body("imageUrl", is("http://example.com/image2.jpg")); // Verify the imageUrl field
    }

    // Test for retrieving posts by user using the GET /posts/user/{userId} endpoint
    @Test
    public void testGetUserPosts() {
        // JSON payload for a new post for userId 3
        String json = "{\"userId\": 3, \"title\": \"User Post\", \"description\": \"First post by user 3.\", \"imageUrl\": \"http://example.com/u3image.jpg\"}";

        // Create the post for userId 3
        given()
                .contentType("application/json") // Set the request content type
                .body(json) // Provide the JSON payload
                .when().post("/posts") // Call the POST endpoint to save the post
                .then()
                .statusCode(200); // Expect HTTP 200 OK

        // Retrieve posts for userId 3 using the GET endpoint
        given()
                .when().get("/posts/user/3") // Call the GET endpoint with the userId
                .then()
                .statusCode(200) // Expect HTTP 200 OK
                .body("size()", is(1)) // Expect one post in the response list
                .body("[0].userId", is(3)); // Verify the userId of the first post
    }
}
