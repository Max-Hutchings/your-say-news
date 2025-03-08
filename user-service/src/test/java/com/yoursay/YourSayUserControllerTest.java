package com.yoursay;

import com.yoursay.model.UserRole;
import com.yoursay.model.YourSayUser;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class YourSayUserControllerTest {

    private String baseUrl = "/your-say-user";

    private YourSayUser existingUser;
    private YourSayUser existingForDeleteUser;

    @BeforeEach
    public void setup() {
        existingUser = new YourSayUser(
                "eve@example.com",
                "eve",
                "Eve",
                "Edwards",
                LocalDate.of(1988, 12, 12));

        existingForDeleteUser = new YourSayUser(
                151L,
                "diana@example.com",
                "diana",
                "Diana",
                "Davis",
                LocalDate.of(1995, 10, 5),
                LocalDate.of(2025, 2, 10),
                UserRole.USER
        );
    }



    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    public void testGetAllYourSayUsersEndpoint() {
        // Send a GET request to the "/all" endpoint and expect a 200 OK response
        Response response = given()
                .when().get(baseUrl + "/all")
                .then()
                .statusCode(200)
                .extract().response();

        Log.info("Response: " + response.getBody().asString());
        List<YourSayUser> users = response.getBody().jsonPath().getList("", YourSayUser.class);
        Log.info("Users: " + users);
        assertTrue(users.size() >= 4, "The list of YourSayUser should be greater than 5");
    }

    @Test
    public void testSaveYourSayUserEndpoint() {
        YourSayUser yourSayUser = new YourSayUser(
                "test@example.com",
                "testYourSayUser",
                "First",
                "Last",
                LocalDate.of(1990, 1, 1));

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(yourSayUser)
            .when().post(baseUrl)
            .then()
            .statusCode(201)
            .body("email", is("test@example.com"));
    }

    @Test
    public void testGetYourSayUserEndpoint() {
        YourSayUser returnedUser = given()
                .pathParam("email", existingUser.getEmail()) // Set the path parameter "email"
                .when().get(baseUrl + "/{email}")
                .then()
                .statusCode(200)
                .extract().as(YourSayUser.class);


        assertEquals(existingUser.getEmail(), returnedUser.getEmail());
        assertEquals(existingUser.getUsername(), returnedUser.getUsername());
        assertEquals(existingUser.getfName(), returnedUser.getfName());
        assertEquals(existingUser.getlName(), returnedUser.getlName());
        assertEquals(existingUser.getDateOfBirth(), returnedUser.getDateOfBirth());
    }

    @Test
    public void testDeleteYourSayUserEndpoint() {
        given()
            .contentType("application/json")
            .body(existingForDeleteUser)
            .when().delete(baseUrl)
            .then()
            .statusCode(200);
    }
}