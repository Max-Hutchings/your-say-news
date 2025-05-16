package com.yoursay;

import com.yoursay.model.UserRole;
import com.yoursay.model.YourSayUser;
import com.yoursay.model.YourSayUserRepository;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class YourSayUserControllerTest {

    private String baseUrl = "/your-say-user";

    private YourSayUser existingUser;
    private YourSayUser existingForDeleteUser;

    @Inject
    YourSayUserRepository yourSayUserRepository;

    @Inject
    Vertx vertx;

    private static final String RAW_PASSWORD = "Test123!";


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
    public void testGetYourSayUserEndpoint() {
        YourSayUser returnedUser = given()
                .pathParam("email", existingUser.getEmail()) // Set the path parameter "email"
                .when().get(baseUrl + "/email/{email}")
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

    @Test
    public void testSaveYourSayUser() {
        // Prepare a fresh user JSON
        Map<String, Object> newUser = Map.of(
                "email", "alice.doe@example.com",
                "username", "alicetest",
                "fName", "Alice",
                "lName", "Test",
                "dateOfBirth", "1992-08-30",
                "password", RAW_PASSWORD // raw; controller will hash
        );

        // Call POST /your-say-user
        given()
                .contentType(ContentType.JSON)
                .body(newUser)
                .when()
                .post(baseUrl + "/sign-up")
                .then()
                .statusCode(201)
                // ensure the returned JSON has an 'id' and the password is not the raw
                .body("id", notNullValue())
                .body("password", not(equalTo(RAW_PASSWORD)))
                // createdDate should be today
                .body("createdDate", equalTo(LocalDate.now().toString()))
                .cookie("YourSayUserId", not(emptyOrNullString()));;
    }

    private void safeTestUserWithPasswordHashed(){
        Map<String, Object> newUser = Map.of(
                "email", "jane.doe@example.com",
                "username", "janetest",
                "fName", "Jane",
                "lName", "Test",
                "dateOfBirth", "1992-08-30",
                "password", RAW_PASSWORD // raw; controller will hash
        );

        // Call POST /your-say-user
        given()
                .contentType(ContentType.JSON)
                .body(newUser)
                .when()
                .post(baseUrl + "/sign-up")
                .then()
                .statusCode(201)
                // ensure the returned JSON has an 'id' and the password is not the raw
                .body("id", notNullValue())
                .body("password", not(equalTo(RAW_PASSWORD)))
                // createdDate should be today
                .body("createdDate", equalTo(LocalDate.now().toString()));
    }

    @Test
    public void testLoginSuccess() {
        // Successful login: correct credentials
        safeTestUserWithPasswordHashed();
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "jane.doe@example.com", "password", RAW_PASSWORD))
                .when()
                .post(baseUrl + "/login")    // note: both login and save share POST, so adjust path if needed
                .then()
                .statusCode(200)
                .body("email", equalTo("jane.doe@example.com"))
                .body("username", equalTo("janetest"))
                .cookie("YourSayUserId", not(emptyOrNullString()));;

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "jane.test@example.com", "password", "wrongpass"))
                .when()
                .post(baseUrl + "login")
                .then()
                // your controller returns null on bad check; Quarkus will map that to 204 No Content
                .statusCode(404);

    }
}